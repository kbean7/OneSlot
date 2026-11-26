package com.reservation.payment.pg;

/**
 * PG사 API 추상화. 실제 구현은 TossPaymentsClient, 테스트에서는 가짜 구현을 쓴다.
 */
public interface PgClient {

    /** 결제 승인. 실패 시 PgException, 시간 초과·네트워크 오류 시 PgTimeoutException */
    PgPaymentResult confirm(String paymentKey, String orderId, int amount);

    /**
     * 결제 취소(환불). cancelAmount가 결제 금액보다 작으면 부분 취소.
     * idempotencyKey가 같으면 PG가 같은 요청을 두 번 처리하지 않는다.
     */
    PgPaymentResult cancel(String paymentKey, String reason, int cancelAmount, String idempotencyKey);
}
