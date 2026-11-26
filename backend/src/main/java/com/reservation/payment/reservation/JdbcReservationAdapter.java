package com.reservation.payment.reservation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * reservations 테이블을 SQL로 직접 읽고 바꾸는 구현체.
 * 예약 코드(엔티티·서비스)를 import하지 않으므로 예약 쪽 클래스가 바뀌어도 영향이 없다.
 *
 * 필요한 컬럼: id, user_id, status, start_at, expires_at, updated_at, total_price
 * ※ total_price가 아직 없으면 개발 DB에 직접 추가:
 *    ALTER TABLE reservations ADD COLUMN total_price INT NULL;
 *
 * JdbcTemplate은 JPA 트랜잭션과 같은 커넥션을 쓰므로 @Transactional 안에서 함께 묶인다.
 */
@Component
public class JdbcReservationAdapter implements ReservationPort {

    private static final String SELECT = """
            SELECT id, user_id, status, start_at, expires_at, total_price
            FROM reservations
            WHERE id = ?
            """;

    private static final RowMapper<ReservationInfo> MAPPER = JdbcReservationAdapter::map;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcReservationAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<ReservationInfo> find(Long reservationId) {
        return jdbc.query(SELECT, MAPPER, reservationId).stream().findFirst();
    }

    @Override
    public Optional<ReservationInfo> findForUpdate(Long reservationId) {
        return jdbc.query(SELECT + " FOR UPDATE", MAPPER, reservationId).stream().findFirst();
    }

    @Override
    public boolean changeStatus(Long reservationId, ReservationState from, ReservationState to) {
        int updated = jdbc.update(
                "UPDATE reservations SET status = ?, updated_at = ? WHERE id = ? AND status = ?",
                to.name(), LocalDateTime.now(clock), reservationId, from.name());
        return updated == 1;
    }

    @Override
    public List<Long> findOverdueUnpaidIds(LocalDateTime now) {
        return jdbc.queryForList(
                "SELECT id FROM reservations WHERE status = ? AND expires_at < ? ORDER BY id",
                Long.class, ReservationState.PENDING_PAYMENT.name(), now);
    }

    @Override
    public List<Long> findIdsByUser(Long userId) {
        return jdbc.queryForList(
                "SELECT id FROM reservations WHERE user_id = ? ORDER BY id DESC",
                Long.class, userId);
    }

    private static ReservationInfo map(ResultSet rs, int rowNum) throws SQLException {
        int price = rs.getInt("total_price");
        Integer totalPrice = rs.wasNull() ? null : price;
        return new ReservationInfo(
                rs.getLong("id"),
                rs.getLong("user_id"),
                ReservationState.from(rs.getString("status")),
                rs.getObject("start_at", LocalDateTime.class),
                rs.getObject("expires_at", LocalDateTime.class),
                totalPrice
        );
    }
}
