package com.reservation.payment.pg;

/** PG가 요청을 거절함 (카드 거절, 잘못된 키 등) */
public class PgException extends RuntimeException {

    private final String code;

    public PgException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
