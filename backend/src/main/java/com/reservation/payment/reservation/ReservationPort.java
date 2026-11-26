package com.reservation.payment.reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 파트가 예약 데이터에 접근하는 유일한 통로.
 * 예약 쪽 구현이 바뀌어도 구현체(JdbcReservationAdapter)만 고치면 된다.
 *
 * 락 순서 규칙: 항상 예약 행을 먼저 잠그고(findForUpdate) 그다음 결제 행을 잠근다.
 */
public interface ReservationPort {

    Optional<ReservationInfo> find(Long reservationId);

    /** 예약 행을 잠그고 조회한다 (SELECT … FOR UPDATE). 트랜잭션 안에서 호출해야 한다. */
    Optional<ReservationInfo> findForUpdate(Long reservationId);

    /** 현재 상태가 from일 때만 to로 바꾼다. 바뀌었으면 true */
    boolean changeStatus(Long reservationId, ReservationState from, ReservationState to);

    /** 결제 마감이 지난 PENDING_PAYMENT 예약 ID 목록 */
    List<Long> findOverdueUnpaidIds(LocalDateTime now);

    /** 사용자의 예약 ID 목록 (결제 내역 조회용) */
    List<Long> findIdsByUser(Long userId);
}
