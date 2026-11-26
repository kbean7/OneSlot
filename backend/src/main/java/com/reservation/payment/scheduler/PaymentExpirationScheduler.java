package com.reservation.payment.scheduler;

import com.reservation.payment.service.PaymentExpirationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 미결제 예약 만료 처리.
 * payment.expiration.enabled=false 로 끌 수 있다 (테스트에서 사용).
 */
@Component
@ConditionalOnProperty(name = "payment.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentExpirationScheduler {

    private final PaymentExpirationService expirationService;

    public PaymentExpirationScheduler(PaymentExpirationService expirationService) {
        this.expirationService = expirationService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireUnpaidReservations() {
        expirationService.run();
    }
}
