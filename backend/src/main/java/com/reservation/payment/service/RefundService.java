package com.reservation.payment.service;

import com.reservation.payment.dto.PaymentDtos.CancelResponse;
import com.reservation.payment.dto.PaymentDtos.RefundQuoteResponse;
import com.reservation.payment.entity.Payment;
import com.reservation.payment.entity.PaymentStatus;
import com.reservation.payment.entity.Refund;
import com.reservation.payment.pg.PgClient;
import com.reservation.payment.pg.PgException;
import com.reservation.payment.pg.PgTimeoutException;
import com.reservation.payment.repository.PaymentRepository;
import com.reservation.payment.repository.RefundRepository;
import com.reservation.payment.reservation.ReservationInfo;
import com.reservation.payment.reservation.ReservationPort;
import com.reservation.payment.reservation.ReservationState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.reservation.payment.service.PaymentService.checkOwner;
import static com.reservation.payment.service.PaymentService.error;
import static com.reservation.payment.service.PaymentService.requirePositive;

/**
 * 예약 취소 및 환불 (RSV_REFUND_001)
 *
 * - 결제 전(PENDING_PAYMENT): 환불 없이 CANCELLED, READY 결제는 CANCELED
 * - 결제 후(CONFIRMED): 환불률 계산 → PG 취소 → 환불 저장, 결제 REFUNDED/PARTIAL_REFUNDED, 예약 CANCELLED
 *   PG 실패(502)·시간 초과(504)면 전체 롤백되어 상태가 그대로라 다시 시도할 수 있다.
 *
 * 예약 행을 잠근 채 PG 취소를 호출한다. 같은 예약에 대한 동시 취소를 한 건으로 줄이기 위해서다.
 */
@Service
public class RefundService {

    private static final String DEFAULT_REASON = "고객 요청 취소";

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReservationPort reservationPort;
    private final PgClient pgClient;
    private final RefundPolicy refundPolicy;
    private final Clock clock;

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         ReservationPort reservationPort,
                         PgClient pgClient,
                         RefundPolicy refundPolicy,
                         Clock clock) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.reservationPort = reservationPort;
        this.pgClient = pgClient;
        this.refundPolicy = refundPolicy;
        this.clock = clock;
    }

    /** 예상 환불 금액 (취소 버튼을 눌렀을 때 먼저 보여줌) */
    @Transactional(readOnly = true)
    public RefundQuoteResponse quote(Long reservationId, Long userId) {
        requirePositive(reservationId, "예약 ID");
        requirePositive(userId, "사용자 ID");

        ReservationInfo r = reservationPort.find(reservationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
        checkOwner(r, userId);

        if (r.status() == ReservationState.PENDING_PAYMENT) {
            return new RefundQuoteResponse(reservationId, 0, 0, 0, LocalDateTime.now(clock));
        }
        Payment paid = findPaid(r);
        RefundPolicy.Quote q = refundPolicy.quote(paid.getAmount(), r.startAt());
        return new RefundQuoteResponse(reservationId, paid.getAmount(), q.rate(), q.amount(), q.calculatedAt());
    }

    /** 예약 취소 + 환불 실행 */
    @Transactional
    public CancelResponse cancel(Long reservationId, Long userId, String reason) {
        requirePositive(reservationId, "예약 ID");
        requirePositive(userId, "사용자 ID");
        String cancelReason = (reason == null || reason.isBlank()) ? DEFAULT_REASON
                : reason.substring(0, Math.min(reason.length(), 200));

        ReservationInfo r = reservationPort.findForUpdate(reservationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
        checkOwner(r, userId);

        return switch (r.status()) {
            case PENDING_PAYMENT -> cancelUnpaid(r);
            case CONFIRMED -> cancelPaid(r, userId, cancelReason);
            case CANCELLED -> throw error(HttpStatus.CONFLICT, "이미 취소된 예약입니다.");
            default -> throw error(HttpStatus.CONFLICT, "취소할 수 없는 예약 상태입니다: " + r.status());
        };
    }

    private CancelResponse cancelUnpaid(ReservationInfo r) {
        if (paymentRepository.existsByReservationIdAndStatus(r.id(), PaymentStatus.IN_PROGRESS)) {
            throw error(HttpStatus.CONFLICT, "결제 승인 중에는 취소할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
        reservationPort.changeStatus(r.id(), ReservationState.PENDING_PAYMENT, ReservationState.CANCELLED);
        paymentRepository.changeStatusByReservation(
                r.id(), PaymentStatus.READY, PaymentStatus.CANCELED, LocalDateTime.now(clock));
        return new CancelResponse(r.id(), ReservationState.CANCELLED.name(), 0, 0, null);
    }

    private CancelResponse cancelPaid(ReservationInfo r, Long userId, String reason) {
        Payment payment = paymentRepository
                .findByReservationIdAndStatusForUpdate(r.id(), PaymentStatus.PAID)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "결제 완료 내역이 없습니다."));
        if (refundRepository.existsByPaymentId(payment.getId())) {
            throw error(HttpStatus.CONFLICT, "이미 환불된 결제입니다.");
        }

        LocalDateTime requestedAt = LocalDateTime.now(clock);
        RefundPolicy.Quote q = refundPolicy.quote(payment.getAmount(), r.startAt()); // 이용 시작 이후면 409

        if (q.amount() > 0) {
            try {
                pgClient.cancel(payment.getPaymentKey(), reason, q.amount(), "refund-" + payment.getId());
            } catch (PgTimeoutException e) {
                throw error(HttpStatus.GATEWAY_TIMEOUT, "환불 응답이 지연되었습니다. 다시 시도해 주세요.");
            } catch (PgException e) {
                throw error(HttpStatus.BAD_GATEWAY, "환불에 실패했습니다: " + e.getMessage());
            }
        }

        refundRepository.save(Refund.completed(payment, q.rate(), q.amount(), reason,
                userId, requestedAt, LocalDateTime.now(clock)));
        PaymentStatus newStatus = q.rate() == 100 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIAL_REFUNDED;
        payment.changeStatus(newStatus);
        reservationPort.changeStatus(r.id(), ReservationState.CONFIRMED, ReservationState.CANCELLED);

        return new CancelResponse(r.id(), ReservationState.CANCELLED.name(), q.rate(), q.amount(),
                newStatus.name());
    }

    private Payment findPaid(ReservationInfo r) {
        if (r.status() != ReservationState.CONFIRMED) {
            throw error(HttpStatus.CONFLICT, "취소할 수 없는 예약 상태입니다: " + r.status());
        }
        return paymentRepository
                .findFirstByReservationIdAndStatusOrderByIdDesc(r.id(), PaymentStatus.PAID)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "결제 완료 내역이 없습니다."));
    }
}
