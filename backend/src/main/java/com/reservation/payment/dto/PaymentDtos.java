package com.reservation.payment.dto;

import java.time.LocalDateTime;

/**
 * 결제 API 요청·응답 모음.
 * userId는 로그인(JWT) 기능이 붙기 전까지 요청에 직접 담는다. (예약 API와 같은 방식)
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** POST /api/payments : 결제 요청 */
    public record PaymentCreateRequest(Long reservationId, Long userId) {}

    /** 결제창(토스 SDK requestPayment)에 넘길 값 */
    public record PaymentReadyResponse(
            Long paymentId,
            Long reservationId,
            String orderId,
            String orderName,
            int amount,
            String customerKey,
            LocalDateTime expiresAt,
            long remainingSeconds
    ) {}

    /** POST /api/payments/confirm : 결제창 성공 후 승인 요청 (successUrl 쿼리값 그대로) */
    public record PaymentConfirmRequest(String paymentKey, String orderId, Integer amount, Long userId) {}

    public record PaymentResponse(
            Long paymentId,
            Long reservationId,
            String orderId,
            int amount,
            String method,
            String status,
            LocalDateTime approvedAt
    ) {}

    /** GET /api/payments/reservations/{id}/refund-quote : 예상 환불 금액 */
    public record RefundQuoteResponse(
            Long reservationId,
            int paidAmount,
            int refundRate,
            int refundAmount,
            LocalDateTime calculatedAt
    ) {}

    /** POST /api/payments/reservations/{id}/cancel : 예약 취소 + 환불 */
    public record CancelRequest(Long userId, String reason) {}

    public record CancelResponse(
            Long reservationId,
            String reservationStatus,
            int refundRate,
            int refundAmount,
            String paymentStatus
    ) {}

    /** GET /api/payments/users/{userId} : 결제·환불 내역 */
    public record PaymentHistoryItem(
            Long paymentId,
            Long reservationId,
            String orderId,
            int amount,
            String status,
            String method,
            LocalDateTime approvedAt,
            Integer refundAmount,
            LocalDateTime refundedAt
    ) {}
}
