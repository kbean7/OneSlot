package com.reservation.space.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 하루 운영 시간 [open, close). closed 면 그날은 예약 불가.
 * 예약 코드에서 09:00~21:00 고정값 대신 이걸 쓴다.
 */
public record OperatingWindow(LocalTime open, LocalTime close, boolean closed) {

    /** 운영 시간 정보가 없는 공간(기존 데이터)은 지금까지와 같은 09:00~21:00 */
    public static final OperatingWindow DEFAULT = new OperatingWindow(LocalTime.of(9, 0), LocalTime.of(21, 0), false);
    public static final OperatingWindow CLOSED = new OperatingWindow(null, null, true);

    public static OperatingWindow of(LocalTime open, LocalTime close) {
        return new OperatingWindow(open, close, false);
    }

    /** start~end 가 같은 날이고 운영 시간 안에 완전히 들어가는지 */
    public boolean covers(LocalDateTime start, LocalDateTime end) {
        if (closed || start == null || end == null || !start.isBefore(end)) {
            return false;
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        return !start.toLocalTime().isBefore(open) && !end.toLocalTime().isAfter(close);
    }
}
