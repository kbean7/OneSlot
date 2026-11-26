package com.reservation.payment.entity;

public enum RefundStatus {

    COMPLETED,  // PG 환불 성공
    FAILED      // PG 환불 실패 (현재는 실패 시 저장하지 않고 롤백)
}
