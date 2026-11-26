package com.reservation.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 결제 API 오류 응답을 {code, message, timestamp} 형식으로 통일 (NFR_DATA_001 ⑤)
 * PaymentController에만 적용된다. 다른 API에는 영향 없음.
 */
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handle(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status, e.getReason(), LocalDateTime.now()));
    }

    public record ErrorResponse(int code, String message, LocalDateTime timestamp) {}
}
