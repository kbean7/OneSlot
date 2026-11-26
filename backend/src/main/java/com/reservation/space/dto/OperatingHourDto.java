package com.reservation.space.dto;

import com.reservation.space.entity.SpaceOperatingHour;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 요일별 운영 시간. dayOfWeek 1=월 ... 7=일, 시간은 "09:00" 형식.
 * 휴무면 closed=true 이고 openTime·closeTime 은 비워도 된다.
 */
public record OperatingHourDto(
        @NotNull(message = "요일을 입력하세요.")
        @Min(value = 1, message = "요일은 1(월)~7(일)입니다.")
        @Max(value = 7, message = "요일은 1(월)~7(일)입니다.")
        Integer dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime,
        boolean closed
) {
    public static OperatingHourDto from(SpaceOperatingHour h) {
        return new OperatingHourDto(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime(), h.getClosed());
    }
}
