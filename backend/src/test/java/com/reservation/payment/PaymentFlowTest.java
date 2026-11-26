package com.reservation.payment;

import com.reservation.payment.dto.PaymentDtos.PaymentConfirmRequest;
import com.reservation.payment.dto.PaymentDtos.PaymentReadyResponse;
import com.reservation.payment.service.PaymentExpirationService;
import com.reservation.payment.service.PaymentService;
import com.reservation.payment.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 결제·환불·만료 통합 테스트 (H2 + 가짜 PG)
 * 실행: ./gradlew test --tests "com.reservation.payment.*"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PaymentFlowTest {

    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;
    private static final int PRICE = 30000;

    @TestConfiguration
    static class FakePgConfig {
        @Bean
        @Primary
        FakePgClient fakePgClient() {
            return new FakePgClient();
        }
    }

    @Autowired PaymentService paymentService;
    @Autowired RefundService refundService;
    @Autowired PaymentExpirationService expirationService;
    @Autowired FakePgClient pg;
    @Autowired JdbcTemplate jdbc;

    private long spaceId;

    @BeforeEach
    void setUp() {
        pg.reset();
        // 예약 코드 없이도 돌도록 테이블을 직접 만든다 (예약 엔티티가 있으면 이미 있으므로 건너뜀)
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS spaces (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
                  capacity INT NOT NULL, location VARCHAR(100), active BOOLEAN NOT NULL, version BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS reservations (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, space_id BIGINT NOT NULL,
                  start_at TIMESTAMP NOT NULL, end_at TIMESTAMP NOT NULL, status VARCHAR(20) NOT NULL,
                  created_at TIMESTAMP, updated_at TIMESTAMP, expires_at TIMESTAMP)
                """);
        jdbc.execute("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS total_price INT");
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM spaces");
        jdbc.update("INSERT INTO spaces (name, capacity, location, active, version) VALUES ('회의실A', 4, '서울', TRUE, 0)");
        spaceId = jdbc.queryForObject("SELECT MAX(id) FROM spaces", Long.class);
    }

    // ========================================
    // 결제 요청
    // ========================================

    @Test
    void 결제요청_같은_예약을_다시_요청하면_주문번호_재사용() {
        long id = pendingReservation(PRICE);
        String first = paymentService.requestPayment(id, USER).orderId();
        String second = paymentService.requestPayment(id, USER).orderId();
        assertEquals(first, second);
        assertEquals(1, count("SELECT COUNT(*) FROM payments"));
    }

    @Test
    void 결제요청_예외_금액없음_마감지남_다른사용자() {
        long noPrice = pendingReservation(null);
        assertStatus(409, () -> paymentService.requestPayment(noPrice, USER));

        long overdue = reservation(USER, "PENDING_PAYMENT", LocalDateTime.now().plusDays(3),
                LocalDateTime.now().minusMinutes(1), PRICE);
        assertStatus(409, () -> paymentService.requestPayment(overdue, USER));

        long id = pendingReservation(PRICE);
        assertStatus(403, () -> paymentService.requestPayment(id, OTHER_USER));
        assertStatus(404, () -> paymentService.requestPayment(9999L, USER));
    }

    // ========================================
    // 승인
    // ========================================

    @Test
    void 승인_성공하면_결제_PAID_예약_CONFIRMED() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);

        var res = paymentService.confirm(confirm(ready, "pk_1", PRICE));

        assertEquals("PAID", res.status());
        assertEquals("CONFIRMED", reservationStatus(id));
        assertEquals(1, paymentService.getHistory(USER).size());
    }

    @Test
    void 승인_금액이_다르면_400이고_PG_호출_안함() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);

        assertStatus(400, () -> paymentService.confirm(confirm(ready, "pk_1", 100)));
        assertEquals(0, pg.confirmCalls.get());
        assertEquals("READY", paymentStatus(ready.orderId()));
    }

    @Test
    void 같은_주문을_다시_승인하면_PG_호출없이_기존_결과() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);

        paymentService.confirm(confirm(ready, "pk_1", PRICE));
        var again = paymentService.confirm(confirm(ready, "pk_1", PRICE));

        assertEquals("PAID", again.status());
        assertEquals(1, pg.confirmCalls.get());
    }

    @Test
    void 확정된_예약을_다른_주문으로_결제하면_409() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        paymentService.confirm(confirm(ready, "pk_1", PRICE));

        assertStatus(409, () -> paymentService.requestPayment(id, USER));
    }

    @Test
    void 동시에_승인_요청이_와도_PG_승인은_한번() throws Exception {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        pg.confirmDelayMillis = 300;

        List<Object> results = runConcurrently(2,
                () -> paymentService.confirm(confirm(ready, "pk_1", PRICE)));

        long ok = results.stream().filter(r -> !(r instanceof Throwable)).count();
        assertEquals(1, ok);
        assertEquals(1, pg.confirmCalls.get());
        assertEquals("CONFIRMED", reservationStatus(id));
    }

    @Test
    void PG가_거절하면_FAILED_예약은_PENDING_유지하고_재결제_가능() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        pg.confirmBehavior = FakePgClient.Behavior.REJECT;

        assertStatus(400, () -> paymentService.confirm(confirm(ready, "pk_1", PRICE)));
        assertEquals("FAILED", paymentStatus(ready.orderId()));
        assertEquals("PENDING_PAYMENT", reservationStatus(id));

        // 새 주문번호로 다시 결제
        pg.confirmBehavior = FakePgClient.Behavior.OK;
        PaymentReadyResponse retry = paymentService.requestPayment(id, USER);
        assertNotEquals(ready.orderId(), retry.orderId());
        paymentService.confirm(confirm(retry, "pk_2", PRICE));
        assertEquals("CONFIRMED", reservationStatus(id));
    }

    @Test
    void PG_시간초과면_504_결제_FAILED() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        pg.confirmBehavior = FakePgClient.Behavior.TIMEOUT;

        assertStatus(504, () -> paymentService.confirm(confirm(ready, "pk_1", PRICE)));
        assertEquals("FAILED", paymentStatus(ready.orderId()));
    }

    @Test
    void 승인_도중_예약이_만료되면_PG결제_자동취소하고_409() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        pg.duringConfirm = () -> jdbc.update("UPDATE reservations SET status = 'EXPIRED' WHERE id = ?", id);

        assertStatus(409, () -> paymentService.confirm(confirm(ready, "pk_1", PRICE)));
        assertEquals(1, pg.cancelCalls.get());
        assertEquals("CANCELED", paymentStatus(ready.orderId()));
        assertEquals("EXPIRED", reservationStatus(id));
    }

    // ========================================
    // 미결제 만료
    // ========================================

    @Test
    void 만료_마감지난_예약은_EXPIRED_READY결제는_CANCELED_두번_실행해도_같음() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        jdbc.update("UPDATE reservations SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), id);

        assertEquals(1, expirationService.run());
        assertEquals(0, expirationService.run());
        assertEquals("EXPIRED", reservationStatus(id));
        assertEquals("CANCELED", paymentStatus(ready.orderId()));
    }

    @Test
    void 만료_승인중인_예약은_건너뛰고_1분넘게_멈춘_결제는_FAILED후_만료() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        jdbc.update("UPDATE reservations SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), id);
        jdbc.update("UPDATE payments SET status = 'IN_PROGRESS', updated_at = ? WHERE order_id = ?",
                LocalDateTime.now(), ready.orderId());

        assertEquals(0, expirationService.run());
        assertEquals("PENDING_PAYMENT", reservationStatus(id));

        jdbc.update("UPDATE payments SET updated_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusMinutes(2), ready.orderId());
        assertEquals(1, expirationService.run());
        assertEquals("FAILED", paymentStatus(ready.orderId()));
        assertEquals("EXPIRED", reservationStatus(id));
    }

    // ========================================
    // 취소·환불
    // ========================================

    @Test
    void 취소_결제전이면_환불없이_CANCELLED() {
        long id = pendingReservation(PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);

        var res = refundService.cancel(id, USER, null);

        assertEquals(0, res.refundAmount());
        assertEquals("CANCELLED", reservationStatus(id));
        assertEquals("CANCELED", paymentStatus(ready.orderId()));
        assertEquals(0, pg.cancelCalls.get());
    }

    @Test
    void 취소_24시간_이상_전이면_전액환불_다시_취소하면_409() {
        long id = paidReservation(LocalDateTime.now().plusDays(3));

        assertEquals(100, refundService.quote(id, USER).refundRate());
        var res = refundService.cancel(id, USER, "일정 변경");

        assertEquals(100, res.refundRate());
        assertEquals(PRICE, res.refundAmount());
        assertEquals("REFUNDED", res.paymentStatus());
        assertEquals("CANCELLED", reservationStatus(id));
        assertEquals(PRICE, pg.lastCancelAmount);
        assertStatus(409, () -> refundService.cancel(id, USER, null));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds"));
    }

    @Test
    void 취소_24시간_미만이면_50퍼센트_부분환불() {
        long id = paidReservation(LocalDateTime.now().plusHours(5));

        var res = refundService.cancel(id, USER, null);

        assertEquals(50, res.refundRate());
        assertEquals(PRICE / 2, res.refundAmount());
        assertEquals("PARTIAL_REFUNDED", res.paymentStatus());
    }

    @Test
    void 취소_이용시작_이후면_409() {
        long id = paidReservation(LocalDateTime.now().plusHours(1));
        jdbc.update("UPDATE reservations SET start_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(5), id);

        assertStatus(409, () -> refundService.cancel(id, USER, null));
    }

    @Test
    void 취소_PG환불_실패면_502이고_상태_그대로() {
        long id = paidReservation(LocalDateTime.now().plusDays(3));
        pg.cancelBehavior = FakePgClient.Behavior.REJECT;

        assertStatus(502, () -> refundService.cancel(id, USER, null));
        assertEquals("CONFIRMED", reservationStatus(id));
        assertEquals(0, count("SELECT COUNT(*) FROM refunds"));
        assertEquals(1, count("SELECT COUNT(*) FROM payments WHERE status = 'PAID'"));
    }

    @Test
    void 취소_다른_사용자면_403() {
        long id = paidReservation(LocalDateTime.now().plusDays(3));
        assertStatus(403, () -> refundService.cancel(id, OTHER_USER, null));
    }

    @Test
    void 동시에_취소해도_환불은_한번() throws Exception {
        long id = paidReservation(LocalDateTime.now().plusDays(3));

        runConcurrently(5, () -> refundService.cancel(id, USER, null));

        assertEquals(1, pg.cancelCalls.get());
        assertEquals(1, count("SELECT COUNT(*) FROM refunds"));
    }

    // ========================================
    // 헬퍼
    // ========================================

    private long pendingReservation(Integer price) {
        return reservation(USER, "PENDING_PAYMENT", LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusMinutes(10), price);
    }

    private long paidReservation(LocalDateTime startAt) {
        long id = reservation(USER, "PENDING_PAYMENT", startAt, LocalDateTime.now().plusMinutes(10), PRICE);
        PaymentReadyResponse ready = paymentService.requestPayment(id, USER);
        paymentService.confirm(confirm(ready, "pk_" + id, PRICE));
        pg.reset();
        return id;
    }

    private long reservation(long userId, String status, LocalDateTime startAt,
                             LocalDateTime expiresAt, Integer price) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO reservations
                  (user_id, space_id, start_at, end_at, status, created_at, updated_at, expires_at, total_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, spaceId, startAt, startAt.plusHours(1), status, now, now, expiresAt, price);
        return jdbc.queryForObject("SELECT MAX(id) FROM reservations", Long.class);
    }

    private static PaymentConfirmRequest confirm(PaymentReadyResponse ready, String paymentKey, int amount) {
        return new PaymentConfirmRequest(paymentKey, ready.orderId(), amount, USER);
    }

    private String reservationStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, id);
    }

    private String paymentStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static void assertStatus(int expected, Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected, e.getStatusCode().value(), e.getReason());
    }

    private static List<Object> runConcurrently(int threads, Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return task.call();
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }
}
