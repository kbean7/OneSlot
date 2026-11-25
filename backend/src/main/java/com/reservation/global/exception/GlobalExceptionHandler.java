package com.reservation.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서비스에서 던지는 ResponseStatusException(예약 코드 포함)과 입력 검증 오류를
 * 같은 JSON 형식으로 돌려준다. 프론트는 message 를 그대로 화면에 띄우면 된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode code = e.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        String error = status != null ? status.name() : "ERROR";
        String message = e.getReason() != null ? e.getReason() : error;
        return ResponseEntity.status(code).body(ErrorResponse.of(code.value(), error, message));
    }

    // @Valid 검증 실패: 필드별 메시지를 errors 에 담고, 첫 번째 메시지를 message 로
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String message = errors.isEmpty() ? "입력값이 올바르지 않습니다." : errors.values().iterator().next();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "BAD_REQUEST", message, errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "BAD_REQUEST", e.getParameterName() + " 값이 필요합니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "BAD_REQUEST", "요청 본문(JSON) 형식이 올바르지 않습니다."));
    }
}
