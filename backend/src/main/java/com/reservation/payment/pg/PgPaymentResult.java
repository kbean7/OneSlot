package com.reservation.payment.pg;

import java.time.LocalDateTime;

/** PG 승인·취소 응답 중 필요한 값 */
public record PgPaymentResult(
        String paymentKey,
        String orderId,
        String status,
        String method,
        long totalAmount,
        LocalDateTime approvedAt
) {
}
