package com.reservation.payment.service;

import com.reservation.payment.dto.PaymentDtos.PaymentConfirmRequest;
import com.reservation.payment.dto.PaymentDtos.PaymentHistoryItem;
import com.reservation.payment.dto.PaymentDtos.PaymentReadyResponse;
import com.reservation.payment.dto.PaymentDtos.PaymentResponse;
import com.reservation.payment.entity.Payment;
import com.reservation.payment.entity.PaymentStatus;
import com.reservation.payment.entity.Refund;
import com.reservation.payment.pg.PgClient;
import com.reservation.payment.pg.PgException;
import com.reservation.payment.pg.PgPaymentResult;
import com.reservation.payment.pg.PgTimeoutException;
import com.reservation.payment.repository.PaymentRepository;
import com.reservation.payment.repository.RefundRepository;
import com.reservation.payment.reservation.ReservationInfo;
import com.reservation.payment.reservation.ReservationPort;
import com.reservation.payment.reservation.ReservationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 결제 요청·승인 (RSV_PAY_001, RSV_PAY_002)
 *
 * 승인은 3단계로 나눈다. PG 호출(최대 10초)을 DB 트랜잭션 밖에 두어 락을 오래 잡지 않기 위해서다.
 *  1) [트랜잭션] 검증 후 결제를 IN_PROGRESS로 (이 상태의 예약은 만료 스케줄러가 건드리지 않음)
 *  2) PG 승인 API 호출
 *  3) [트랜잭션] 결제 PAID + 예약 PENDING_PAYMENT→CONFIRMED (조건부 UPDATE)
 *     예약이 그사이 만료·취소됐으면 PG 결제를 자동 취소하고 409
 *
 * 락 순서: 예약 행 → 결제 행 (만료 스케줄러·취소와 같은 순서라 데드락을 피함)
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReservationPort reservationPort;
    private final PgClient pgClient;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          RefundRepository refundRepository,
                          ReservationPort reservationPort,
                          PgClient pgClient,
                          TransactionTemplate paymentTransactionTemplate,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.reservationPort = reservationPort;
        this.pgClient = pgClient;
        this.tx = paymentTransactionTemplate;
        this.clock = clock;
    }

    // ========================================
    // 1. 결제 요청 (READY 생성, 주문번호 발급)
    // ========================================

    @Transactional
    public PaymentReadyResponse requestPayment(Long reservationId, Long userId) {
        requirePositive(reservationId, "예약 ID");
        requirePositive(userId, "사용자 ID");
        LocalDateTime now = now();

        ReservationInfo r = reservationPort.findForUpdate(reservationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
        checkOwner(r, userId);

        if (r.status() == ReservationState.CONFIRMED) {
            throw error(HttpStatus.CONFLICT, "이미 결제가 완료된 예약입니다.");
        }
        if (r.status() != ReservationState.PENDING_PAYMENT) {
            throw error(HttpStatus.CONFLICT, "결제할 수 없는 예약 상태입니다: " + r.status());
        }
        if (r.isPaymentDeadlinePassed(now)) {
            throw error(HttpStatus.CONFLICT, "결제 마감 시간이 지났습니다.");
        }
        if (r.totalPrice() == null || r.totalPrice() <= 0) {
            throw error(HttpStatus.CONFLICT, "예약 금액이 정해지지 않았습니다.");
        }
        if (paymentRepository.existsByReservationIdAndStatus(reservationId, PaymentStatus.IN_PROGRESS)) {
            throw error(HttpStatus.CONFLICT, "결제 승인이 진행 중입니다.");
        }

        int amount = r.totalPrice();
        Payment payment = paymentRepository
                .findFirstByReservationIdAndStatusOrderByIdDesc(reservationId, PaymentStatus.READY)
                .filter(p -> p.getAmount() == amount)      // 같은 금액의 READY 주문번호는 재사용
                .orElse(null);

        if (payment == null) {
            // 금액이 바뀐 이전 READY 주문은 닫는다
            paymentRepository.changeStatusByReservation(
                    reservationId, PaymentStatus.READY, PaymentStatus.CANCELED, now);
            payment = paymentRepository.save(Payment.ready(reservationId, newOrderId(reservationId), amount));
        }

        long remaining = Math.max(0, Duration.between(now, r.expiresAt()).getSeconds());
        return new PaymentReadyResponse(
                payment.getId(),
                reservationId,
                payment.getOrderId(),
                "OneSlot 공간 예약 #" + reservationId,
                payment.getAmount(),
                "user-" + userId,
                r.expiresAt(),
                remaining
        );
    }

    // ========================================
    // 2. 결제 승인·검증
    // ========================================

    public PaymentResponse confirm(PaymentConfirmRequest request) {
        if (request == null || isBlank(request.paymentKey()) || isBlank(request.orderId())
                || request.amount() == null || request.amount() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "paymentKey, orderId, amount를 확인해 주세요.");
        }
        requirePositive(request.userId(), "사용자 ID");

        // 1) 검증 + IN_PROGRESS
        Approval approval;
        try {
            approval = tx.execute(status -> startApproval(request));
        } catch (DataIntegrityViolationException e) {
            throw error(HttpStatus.CONFLICT, "이미 사용된 결제 키입니다.");
        }
        if (approval.alreadyPaid() != null) {
            return approval.alreadyPaid();     // 같은 주문 재요청: PG 호출 없이 기존 결과
        }

        // 2) PG 승인 (트랜잭션 밖)
        PgPaymentResult result;
        try {
            result = pgClient.confirm(request.paymentKey(), request.orderId(), request.amount());
        } catch (PgTimeoutException e) {
            markFailed(approval.paymentId());
            throw error(HttpStatus.GATEWAY_TIMEOUT, "결제 승인 응답이 지연되었습니다. 다시 시도해 주세요.");
        } catch (PgException e) {
            markFailed(approval.paymentId());
            throw error(HttpStatus.BAD_REQUEST, "결제가 승인되지 않았습니다: " + e.getMessage());
        }

        // 3) PAID + 예약 확정
        Completion completion = tx.execute(status -> completeApproval(approval, result));
        if (completion.response() != null) {
            return completion.response();
        }

        // 예약이 그사이 만료·취소됨 → PG 결제 자동 취소
        try {
            pgClient.cancel(request.paymentKey(), "예약 만료로 결제 자동 취소",
                    request.amount(), "auto-cancel-" + approval.paymentId());
        } catch (RuntimeException e) {
            log.error("[수동 확인 필요] 자동 취소 실패 paymentId={}, paymentKey={}",
                    approval.paymentId(), request.paymentKey(), e);
        }
        throw error(HttpStatus.CONFLICT, "결제 마감이 지나 예약이 만료되었습니다. 결제는 자동 취소됩니다.");
    }

    private Approval startApproval(PaymentConfirmRequest req) {
        Long reservationId = paymentRepository.findReservationIdByOrderId(req.orderId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."));

        ReservationInfo r = reservationPort.findForUpdate(reservationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
        checkOwner(r, req.userId());

        Payment p = paymentRepository.findByOrderIdForUpdate(req.orderId()).orElseThrow();

        if (p.getStatus() == PaymentStatus.PAID) {
            if (req.paymentKey().equals(p.getPaymentKey()) && p.getAmount().equals(req.amount())) {
                return new Approval(p.getId(), reservationId, toResponse(p));
            }
            throw error(HttpStatus.CONFLICT, "이미 결제가 완료된 주문입니다.");
        }
        if (p.getStatus() == PaymentStatus.IN_PROGRESS) {
            throw error(HttpStatus.CONFLICT, "이미 승인 처리 중인 결제입니다.");
        }
        if (p.getStatus() != PaymentStatus.READY) {
            throw error(HttpStatus.CONFLICT, "유효하지 않은 결제입니다. 결제를 다시 요청해 주세요.");
        }
        // 금액 위변조 검증: 요청 금액 = 주문 금액 = 예약 금액
        if (!p.getAmount().equals(req.amount()) || r.totalPrice() == null
                || !r.totalPrice().equals(req.amount())) {
            throw error(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다.");
        }
        if (r.status() == ReservationState.CONFIRMED) {
            throw error(HttpStatus.CONFLICT, "이미 확정된 예약입니다.");
        }
        if (r.status() != ReservationState.PENDING_PAYMENT) {
            throw error(HttpStatus.CONFLICT, "결제할 수 없는 예약 상태입니다: " + r.status());
        }
        if (r.isPaymentDeadlinePassed(now())) {
            throw error(HttpStatus.CONFLICT, "결제 마감 시간이 지났습니다.");
        }

        p.startApproval(req.paymentKey());
        paymentRepository.saveAndFlush(p);
        return new Approval(p.getId(), reservationId, null);
    }

    private Completion completeApproval(Approval approval, PgPaymentResult result) {
        reservationPort.findForUpdate(approval.reservationId());
        Payment p = paymentRepository.findByIdForUpdate(approval.paymentId()).orElseThrow();

        boolean confirmed = p.getStatus() == PaymentStatus.IN_PROGRESS
                && reservationPort.changeStatus(p.getReservationId(),
                        ReservationState.PENDING_PAYMENT, ReservationState.CONFIRMED);

        if (!confirmed) {
            p.changeStatus(PaymentStatus.CANCELED);
            log.warn("승인 완료 후 예약 확정 실패 → 자동 취소 대상 paymentId={}", p.getId());
            return new Completion(null);
        }

        LocalDateTime approvedAt = result.approvedAt() != null ? result.approvedAt() : now();
        p.markPaid(result.method(), approvedAt);
        paymentRepository.saveAndFlush(p);
        return new Completion(toResponse(p));
    }

    private void markFailed(Long paymentId) {
        tx.executeWithoutResult(status -> paymentRepository.changeStatus(
                paymentId, PaymentStatus.IN_PROGRESS, PaymentStatus.FAILED, now()));
    }

    // ========================================
    // 3. 결제·환불 내역
    // ========================================

    @Transactional(readOnly = true)
    public List<PaymentHistoryItem> getHistory(Long userId) {
        requirePositive(userId, "사용자 ID");
        List<Long> reservationIds = reservationPort.findIdsByUser(userId);
        if (reservationIds.isEmpty()) {
            return List.of();
        }
        List<Payment> payments = paymentRepository.findByReservationIdInOrderByIdDesc(reservationIds);
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, Refund> refunds = refundRepository
                .findByPaymentIdIn(payments.stream().map(Payment::getId).toList())
                .stream()
                .collect(Collectors.toMap(rf -> rf.getPayment().getId(), Function.identity()));

        return payments.stream().map(p -> {
            Refund rf = refunds.get(p.getId());
            return new PaymentHistoryItem(
                    p.getId(), p.getReservationId(), p.getOrderId(), p.getAmount(),
                    p.getStatus().name(), p.getMethod(), p.getApprovedAt(),
                    rf == null ? null : rf.getRefundAmount(),
                    rf == null ? null : rf.getCompletedAt());
        }).toList();
    }

    // ========================================
    // 공통
    // ========================================

    static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getReservationId(), p.getOrderId(), p.getAmount(),
                p.getMethod(), p.getStatus().name(), p.getApprovedAt());
    }

    private static String newOrderId(Long reservationId) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "OS-" + reservationId + "-" + random;
    }

    static void checkOwner(ReservationInfo r, Long userId) {
        if (!r.isOwnedBy(userId)) {
            throw error(HttpStatus.FORBIDDEN, "본인의 예약만 결제·취소할 수 있습니다.");
        }
    }

    static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw error(HttpStatus.BAD_REQUEST, name + "를 확인해 주세요.");
        }
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 1단계 결과: alreadyPaid가 있으면 이미 결제 완료된 같은 요청 */
    private record Approval(Long paymentId, Long reservationId, PaymentResponse alreadyPaid) {}

    /** 3단계 결과: response가 null이면 예약 확정 실패 (자동 취소 필요) */
    private record Completion(PaymentResponse response) {}
}
