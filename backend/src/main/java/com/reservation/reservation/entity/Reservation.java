
package com.reservation.reservation.entity;

import com.reservation.space.entity.Space;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@Setter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 예약한 사용자 ID
    @Column(nullable = false)
    private Long userId;

    // 예약한 회의실
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    // 예약 시작 시간
    @Column(nullable = false)
    private LocalDateTime startAt;

    // 예약 종료 시간
    @Column(nullable = false)
    private LocalDateTime endAt;

    // 예약 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status =
            ReservationStatus.PENDING_PAYMENT;

    // 예약 생성 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 예약 수정 시간
    private LocalDateTime updatedAt;

    // 결제 대기 만료 시간
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
