package com.reservation.payment.reservation;

import java.time.LocalDateTime;

/** 결제에 필요한 예약 정보만 담은 읽기 전용 값 */
public record ReservationInfo(
        Long id,
        Long userId,
        ReservationState status,
        LocalDateTime startAt,
        LocalDateTime expiresAt,
        Integer totalPrice
) {

    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public boolean isPaymentDeadlinePassed(LocalDateTime now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }
}
