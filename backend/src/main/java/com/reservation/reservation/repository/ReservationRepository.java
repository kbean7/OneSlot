package com.reservation.reservation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.reservation.entity.Reservation;
import com.reservation.reservation.entity.ReservationStatus;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    // 1. 사용자별 예약 목록 조회
    List<Reservation> findByUserId(Long userId);

    // 2. 예약 생성 및 캘린더 조회 시 중복 검사
    // 만료된 결제대기 예약 제외
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.space.id = :spaceId
          AND r.status IN :statuses
          AND (
              r.status = com.reservation.reservation.entity.ReservationStatus.CONFIRMED
              OR (
                  r.status = com.reservation.reservation.entity.ReservationStatus.PENDING_PAYMENT
                  AND r.expiresAt > CURRENT_TIMESTAMP
              )
          )
          AND r.startAt < :endAt
          AND r.endAt > :startAt
        ORDER BY r.startAt ASC
    """)
    List<Reservation> findOverlappingReservations(
            @Param("spaceId") Long spaceId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("statuses") Collection<ReservationStatus> statuses
    );

    // 3. 예약 변경 시 자기 자신을 제외하고 중복 검사
    // 만료된 결제대기 예약 제외
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.space.id = :spaceId
          AND r.id <> :reservationId
          AND r.status IN :statuses
          AND (
              r.status = com.reservation.reservation.entity.ReservationStatus.CONFIRMED
              OR (
                  r.status = com.reservation.reservation.entity.ReservationStatus.PENDING_PAYMENT
                  AND r.expiresAt > CURRENT_TIMESTAMP
              )
          )
          AND r.startAt < :endAt
          AND r.endAt > :startAt
        ORDER BY r.startAt ASC
    """)
    List<Reservation> findOverlappingReservationsExcludingSelf(
            @Param("spaceId") Long spaceId,
            @Param("reservationId") Long reservationId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("statuses") Collection<ReservationStatus> statuses
    );
}