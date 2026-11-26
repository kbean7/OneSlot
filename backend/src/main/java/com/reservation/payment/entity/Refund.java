package com.reservation.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 환불 (ERD RSV_ERD_004 refunds)
 * - 결제 1건당 환불 최대 1건 (payment_id 유니크)
 * - refund_policy_id는 확장(P1) refund_policies 테이블 연결용. 지금은 NULL
 */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(name = "refund_policy_id")
    private Long refundPolicyId;

    // 환불률 (%) : 100 또는 50
    @Column(nullable = false)
    private Integer refundRate;

    @Column(nullable = false)
    private Integer refundAmount;

    @Column(length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    // 환불 요청자 (사용자 ID)
    private Long requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    public static Refund completed(Payment payment, int rate, int amount, String reason,
                                   Long requestedBy, LocalDateTime requestedAt,
                                   LocalDateTime completedAt) {
        Refund r = new Refund();
        r.payment = payment;
        r.refundRate = rate;
        r.refundAmount = amount;
        r.reason = reason;
        r.status = RefundStatus.COMPLETED;
        r.requestedBy = requestedBy;
        r.requestedAt = requestedAt;
        r.completedAt = completedAt;
        return r;
    }
}
