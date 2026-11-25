package com.reservation.global.exception;

import java.util.Map;

/**
 * 모든 API 오류 응답 형식.
 * 예) {"status":409,"error":"CONFLICT","message":"이미 사용 중인 아이디입니다.","errors":{}}
 * errors 는 입력값 검증 실패 때만 채워진다: {"loginId":"아이디는 영문·숫자 4~20자입니다."}
 */
public record ErrorResponse(int status, String error, String message, Map<String, String> errors) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Map.of());
    }
}
