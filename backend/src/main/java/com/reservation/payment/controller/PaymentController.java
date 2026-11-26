package com.reservation.payment.controller;

import com.reservation.payment.dto.PaymentDtos.CancelRequest;
import com.reservation.payment.dto.PaymentDtos.CancelResponse;
import com.reservation.payment.dto.PaymentDtos.PaymentConfirmRequest;
import com.reservation.payment.dto.PaymentDtos.PaymentCreateRequest;
import com.reservation.payment.dto.PaymentDtos.PaymentHistoryItem;
import com.reservation.payment.dto.PaymentDtos.PaymentReadyResponse;
import com.reservation.payment.dto.PaymentDtos.PaymentResponse;
import com.reservation.payment.dto.PaymentDtos.RefundQuoteResponse;
import com.reservation.payment.service.PaymentService;
import com.reservation.payment.service.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 결제·환불 API
 *
 * POST /api/payments                                       결제 요청 (주문번호 발급)
 * POST /api/payments/confirm                               결제창 성공 후 승인
 * GET  /api/payments/users/{userId}                        결제·환불 내역
 * GET  /api/payments/reservations/{id}/refund-quote        예상 환불 금액
 * POST /api/payments/reservations/{id}/cancel              예약 취소 + 환불
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<PaymentReadyResponse> request(@RequestBody PaymentCreateRequest request) {
        PaymentReadyResponse body = paymentService.requestPayment(request.reservationId(), request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/confirm")
    public PaymentResponse confirm(@RequestBody PaymentConfirmRequest request) {
        return paymentService.confirm(request);
    }

    @GetMapping("/users/{userId}")
    public List<PaymentHistoryItem> history(@PathVariable Long userId) {
        return paymentService.getHistory(userId);
    }

    @GetMapping("/reservations/{reservationId}/refund-quote")
    public RefundQuoteResponse refundQuote(@PathVariable Long reservationId, @RequestParam Long userId) {
        return refundService.quote(reservationId, userId);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public CancelResponse cancel(@PathVariable Long reservationId, @RequestBody CancelRequest request) {
        return refundService.cancel(reservationId, request.userId(), request.reason());
    }
}
