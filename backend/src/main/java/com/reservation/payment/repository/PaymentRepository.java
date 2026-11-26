package com.reservation.payment.repository;

import com.reservation.payment.entity.Payment;
import com.reservation.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    // 잠그기 전에 예약 ID만 먼저 알아낸다 (엔티티를 미리 읽으면 잠근 뒤에도 옛 값이 남을 수 있음)
    @Query("SELECT p.reservationId FROM Payment p WHERE p.orderId = :orderId")
    Optional<Long> findReservationIdByOrderId(@Param("orderId") String orderId);

    // 승인·환불 처리 시 결제 행 잠금 (SELECT … FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM Payment p
        WHERE p.reservationId = :reservationId AND p.status = :status
        ORDER BY p.id DESC
    """)
    List<Payment> findByReservationIdAndStatusForUpdate(@Param("reservationId") Long reservationId,
                                                        @Param("status") PaymentStatus status);

    Optional<Payment> findFirstByReservationIdAndStatusOrderByIdDesc(
            Long reservationId, PaymentStatus status);

    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    List<Payment> findByReservationIdInOrderByIdDesc(List<Long> reservationIds);

    // 상태 조건부 변경: 현재 상태가 from일 때만 바뀐다 (동시 처리 시 한쪽만 성공)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Payment p SET p.status = :to, p.updatedAt = :now
        WHERE p.id = :id AND p.status = :from
    """)
    int changeStatus(@Param("id") Long id,
                     @Param("from") PaymentStatus from,
                     @Param("to") PaymentStatus to,
                     @Param("now") LocalDateTime now);

    // 예약 하나의 READY 결제를 모두 CANCELED로
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Payment p SET p.status = :to, p.updatedAt = :now
        WHERE p.reservationId = :reservationId AND p.status = :from
    """)
    int changeStatusByReservation(@Param("reservationId") Long reservationId,
                                  @Param("from") PaymentStatus from,
                                  @Param("to") PaymentStatus to,
                                  @Param("now") LocalDateTime now);

    // 1분 넘게 IN_PROGRESS로 남은 결제 (서버 장애 등)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Payment p SET p.status = :to, p.updatedAt = :now
        WHERE p.status = :from AND p.updatedAt < :cutoff
    """)
    int failStale(@Param("from") PaymentStatus from,
                  @Param("to") PaymentStatus to,
                  @Param("cutoff") LocalDateTime cutoff,
                  @Param("now") LocalDateTime now);
}
