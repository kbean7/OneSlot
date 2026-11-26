package com.reservation.space.dto;

import com.reservation.space.entity.Space;

import java.util.List;

/** 공간 상세 (RSV_UI_006 정보 영역, 관리자 수정 화면) */
public record SpaceDetailResponse(
        Long id,
        String name,
        String location,
        Integer capacity,
        Integer pricePerHour,
        String description,
        boolean active,
        List<ImageResponse> images,
        List<OperatingHourDto> operatingHours
) {
    public static SpaceDetailResponse of(Space s, List<ImageResponse> images, List<OperatingHourDto> hours) {
        return new SpaceDetailResponse(s.getId(), s.getName(), s.getLocation(), s.getCapacity(),
                s.getPricePerHour(), s.getDescription(), Boolean.TRUE.equals(s.getActive()), images, hours);
    }
}
