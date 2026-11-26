package com.reservation.payment.entity;

public enum PaymentStatus {

    READY,             // 결제 요청됨 (주문번호 발급)
    IN_PROGRESS,       // PG 승인 진행 중
    PAID,              // 결제 완료
    FAILED,            // 승인 실패·시간 초과
    CANCELED,          // 결제 전 취소·만료
    REFUNDED,          // 전액 환불
    PARTIAL_REFUNDED   // 부분 환불
}
