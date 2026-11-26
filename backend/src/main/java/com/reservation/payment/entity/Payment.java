package com.reservation.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 (ERD RSV_ERD_004 payments)
 * - 예약 1건에 결제 여러 건 가능 (실패 후 재시도)
 * - order_id, payment_key 유니크로 중복 승인 방지
 * - 예약은 reservation_id 값으로만 참조한다 (예약 엔티티에 의존하지 않음)
 */
@Entity
@Table(
        name = "payments",
        indexes = @Index(name = "idx_payments_reservation_status",
                columnList = "reservation_id, status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    // 서버가 발급하는 주문번호 (토스 orderId: 6~64자, 영문·숫자·-·_)
    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    // PG가 발급하는 결제 키 (승인 시작 시 저장)
    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private Integer amount;

    @Column(length = 30)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime approvedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static Payment ready(Long reservationId, String orderId, int amount) {
        Payment p = new Payment();
        p.reservationId = reservationId;
        p.orderId = orderId;
        p.amount = amount;
        p.status = PaymentStatus.READY;
        return p;
    }

    public void startApproval(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.IN_PROGRESS;
    }

    public void markPaid(String method, LocalDateTime approvedAt) {
        this.method = method;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.PAID;
    }

    public void changeStatus(PaymentStatus status) {
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
