package com.reservation.payment.pg;

/** PG 응답 시간 초과 또는 네트워크 오류 */
public class PgTimeoutException extends RuntimeException {

    public PgTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
