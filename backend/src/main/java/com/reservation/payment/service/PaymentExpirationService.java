package com.reservation.payment.service;

import com.reservation.payment.entity.PaymentStatus;
import com.reservation.payment.repository.PaymentRepository;
import com.reservation.payment.reservation.ReservationPort;
import com.reservation.payment.reservation.ReservationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 미결제 예약 자동 만료 (RSV_PAY_003)
 * 1) 1분 넘게 IN_PROGRESS로 남은 결제 → FAILED
 * 2) 결제 마감이 지난 PENDING_PAYMENT 예약 중 IN_PROGRESS 결제가 없는 것 → EXPIRED, READY 결제 → CANCELED
 *
 * 예약마다 별도 트랜잭션이라 한 건이 실패해도 나머지는 처리되고, 실패한 건은 다음 주기에 다시 처리된다.
 * 여러 번 실행해도 결과가 같다 (조건부 UPDATE).
 */
@Service
public class PaymentExpirationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpirationService.class);
    static final Duration STALE_IN_PROGRESS = Duration.ofMinutes(1);

    private final PaymentRepository paymentRepository;
    private final ReservationPort reservationPort;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentExpirationService(PaymentRepository paymentRepository,
                                    ReservationPort reservationPort,
                                    TransactionTemplate paymentTransactionTemplate,
                                    Clock clock) {
        this.paymentRepository = paymentRepository;
        this.reservationPort = reservationPort;
        this.tx = paymentTransactionTemplate;
        this.clock = clock;
    }

    /** @return 만료 처리한 예약 수 */
    public int run() {
        LocalDateTime now = LocalDateTime.now(clock);

        Integer failed = tx.execute(status -> paymentRepository.failStale(
                PaymentStatus.IN_PROGRESS, PaymentStatus.FAILED, now.minus(STALE_IN_PROGRESS), now));
        if (failed != null && failed > 0) {
            log.warn("IN_PROGRESS로 1분 넘게 남은 결제 {}건을 FAILED로 변경", failed);
        }

        List<Long> candidates = reservationPort.findOverdueUnpaidIds(now);
        int expired = 0;
        for (Long id : candidates) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> expireOne(id, now)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("예약 만료 처리 실패 reservationId={} (다음 주기에 재시도)", id, e);
            }
        }
        if (expired > 0) {
            log.info("미결제 예약 {}건 만료 처리", expired);
        }
        return expired;
    }

    private boolean expireOne(Long reservationId, LocalDateTime now) {
        var r = reservationPort.findForUpdate(reservationId).orElse(null);
        if (r == null || r.status() != ReservationState.PENDING_PAYMENT || !r.isPaymentDeadlinePassed(now)) {
            return false;
        }
        if (paymentRepository.existsByReservationIdAndStatus(reservationId, PaymentStatus.IN_PROGRESS)) {
            return false;   // 승인 진행 중인 예약은 건너뜀
        }
        boolean changed = reservationPort.changeStatus(
                reservationId, ReservationState.PENDING_PAYMENT, ReservationState.EXPIRED);
        if (changed) {
            paymentRepository.changeStatusByReservation(
                    reservationId, PaymentStatus.READY, PaymentStatus.CANCELED, now);
        }
        return changed;
    }
}
