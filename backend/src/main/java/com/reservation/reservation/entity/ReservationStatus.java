
package com.reservation.reservation.entity;

public enum ReservationStatus {

    PENDING_PAYMENT,  // 결제 대기
    CONFIRMED,        // 예약 확정
    CANCELLED,        // 예약 취소
    EXPIRED,          // 예약 만료
    COMPLETED         // 이용 완료
}
