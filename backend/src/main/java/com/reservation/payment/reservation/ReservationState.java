package com.reservation.payment.reservation;

/**
 * 결제 파트에서 보는 예약 상태.
 * reservations.status 컬럼 문자열과 이름이 같아야 한다.
 * (예약 쪽 enum 철자가 바뀌면 여기만 맞추면 됨. 예: CANCELLED → CANCELED)
 */
public enum ReservationState {

    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED,
    NO_SHOW,
    UNKNOWN;

    public static ReservationState from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
