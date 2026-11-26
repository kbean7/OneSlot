package com.reservation.space;

import com.reservation.space.dto.PageResponse;
import com.reservation.space.entity.OperatingWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 도는 단위 테스트
class OperatingWindowTest {

    private final OperatingWindow window = OperatingWindow.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
    private final LocalDateTime day = LocalDateTime.of(2026, 12, 1, 0, 0);

    @Test
    void 운영시간_안이면_가능() {
        assertThat(window.covers(day.withHour(10), day.withHour(12))).isTrue();
        assertThat(window.covers(day.withHour(16), day.withHour(18))).isTrue();   // 마감 시각에 딱 끝나는 건 OK
    }

    @Test
    void 운영시간을_벗어나면_불가() {
        assertThat(window.covers(day.withHour(9).withMinute(50), day.withHour(11))).isFalse();
        assertThat(window.covers(day.withHour(17), day.withHour(18).withMinute(10))).isFalse();
    }

    @Test
    void 휴무일과_잘못된_범위는_불가() {
        assertThat(OperatingWindow.CLOSED.covers(day.withHour(10), day.withHour(11))).isFalse();
        assertThat(window.covers(day.withHour(12), day.withHour(11))).isFalse();             // 끝이 시작보다 빠름
        assertThat(window.covers(day.withHour(17), day.plusDays(1).withHour(11))).isFalse(); // 날짜를 넘김
    }

    @Test
    void 운영시간_미등록_공간은_09시부터_21시() {
        assertThat(OperatingWindow.DEFAULT.covers(day.withHour(9), day.withHour(21))).isTrue();
        assertThat(OperatingWindow.DEFAULT.covers(day.withHour(20), day.withHour(22))).isFalse();
    }

    @Test
    void 페이지_자르기() {
        List<Integer> all = List.of(1, 2, 3, 4, 5);
        PageResponse<Integer> p = PageResponse.slice(all, 1, 2);
        assertThat(p.content()).containsExactly(3, 4);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(PageResponse.slice(all, 9, 2).content()).isEmpty();
    }
}
