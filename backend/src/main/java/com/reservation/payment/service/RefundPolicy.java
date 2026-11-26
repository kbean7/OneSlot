package com.reservation.payment.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 취소 시점별 환불 금액 계산 (RSV_REFUND_001)
 * - 이용 시작까지 24시간 이상: 100%
 * - 24시간 미만: 50%
 * - 이용 시작 이후: 취소 불가 (409)
 * - 금액은 원 단위 내림
 * 예상 환불 금액 조회와 실제 환불에서 이 클래스 하나만 사용한다.
 */
@Component
public class RefundPolicy {

    static final Duration FULL_REFUND_THRESHOLD = Duration.ofHours(24);

    private final Clock clock;

    public RefundPolicy(Clock clock) {
        this.clock = clock;
    }

    public Quote quote(int paidAmount, LocalDateTime useStartAt) {
        if (paidAmount <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (useStartAt == null || !now.isBefore(useStartAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이용 시작 이후에는 취소할 수 없습니다.");
        }
        int rate = Duration.between(now, useStartAt).compareTo(FULL_REFUND_THRESHOLD) >= 0 ? 100 : 50;
        // 정수 나눗셈 = 원 단위 내림 (금액이 양수이므로)
        int amount = (int) ((long) paidAmount * rate / 100);
        return new Quote(rate, amount, now);
    }

    /** 환불률·금액·계산 기준 시각 */
    public record Quote(int rate, int amount, LocalDateTime calculatedAt) {}
}
