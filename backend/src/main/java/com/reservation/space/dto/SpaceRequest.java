package com.reservation.space.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** 관리자 공간 등록·수정 (RSV_UI_009-1) */
public record SpaceRequest(

        @NotBlank(message = "공간명을 입력하세요.")
        @Size(max = 100, message = "공간명은 100자 이하입니다.")
        String name,

        @NotBlank(message = "지역을 입력하세요.")
        @Size(max = 100, message = "지역은 100자 이하입니다.")
        String location,

        @NotNull(message = "수용 인원을 입력하세요.")
        @Min(value = 1, message = "수용 인원은 1명 이상입니다.")
        @Max(value = 100, message = "수용 인원은 100명 이하입니다.")
        Integer capacity,

        @NotNull(message = "시간당 요금을 입력하세요.")
        @Min(value = 0, message = "요금은 0원 이상입니다.")
        @Max(value = 10_000_000, message = "요금이 너무 큽니다.")
        Integer pricePerHour,

        @Size(max = 1000, message = "소개는 1000자 이하입니다.")
        String description,

        // 비우면 월~일 09:00~21:00 으로 만든다. 넣을 때는 7개 요일 모두
        @Valid
        List<OperatingHourDto> operatingHours
) {
}
