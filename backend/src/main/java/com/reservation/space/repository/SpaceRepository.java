
package com.reservation.space.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.reservation.entity.ReservationStatus;
import com.reservation.space.entity.Space;

import jakarta.persistence.LockModeType;

public interface SpaceRepository extends JpaRepository<Space, Long> {

    // ========================================
    // 1. 비관적 락
    // 기존 예약 생성·변경·취소 기능에서 사용
    // ========================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Space s WHERE s.id = :id")
    Optional<Space> findByIdForUpdate(@Param("id") Long id);


    // ========================================
    // 2. 낙관적 락
    // 별도의 비교 실험에서 사용
    // ========================================

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT s FROM Space s WHERE s.id = :id")
    Optional<Space> findByIdForOptimisticUpdate(@Param("id") Long id);


    // ========================================
    // 3. 공간 조회 (박경빈)
    // ========================================

    List<Space> findByActiveTrue();

    // start~end 에 예약이 걸려 있는 공간 ID
    // (확정 예약 + 아직 결제 마감 전인 결제대기 예약. 마감이 지난 결제대기는 자리를 차지하지 않음)
    @Query("""
        SELECT DISTINCT r.space.id FROM Reservation r
        WHERE r.startAt < :endAt
          AND r.endAt > :startAt
          AND (r.status IN :blockingStatuses
               OR (r.status = :pendingStatus AND r.expiresAt > :now))
    """)
    List<Long> findBusySpaceIds(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("blockingStatuses") Collection<ReservationStatus> blockingStatuses,
            @Param("pendingStatus") ReservationStatus pendingStatus,
            @Param("now") LocalDateTime now);

    // 기간 내 예약이 많은 공간 ID 순서 (메인 인기 공간)
    @Query("""
        SELECT r.space.id FROM Reservation r
        WHERE r.createdAt >= :from
          AND r.status IN :statuses
        GROUP BY r.space.id
        ORDER BY COUNT(r) DESC
    """)
    List<Long> findPopularSpaceIds(
            @Param("from") LocalDateTime from,
            @Param("statuses") Collection<ReservationStatus> statuses,
            Pageable pageable);
}
