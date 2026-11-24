
package com.reservation.space.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
