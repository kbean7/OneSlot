package com.reservation.space.dto;

import com.reservation.space.entity.Space;

/** 목록·카드용 (메인 인기 공간, 검색 결과, 관리자 목록) */
public record SpaceSummaryResponse(
        Long id,
        String name,
        String location,
        Integer capacity,
        Integer pricePerHour,
        String thumbnailUrl,   // 사진이 없으면 null
        boolean active
) {
    public static SpaceSummaryResponse of(Space s, String thumbnailUrl) {
        return new SpaceSummaryResponse(s.getId(), s.getName(), s.getLocation(), s.getCapacity(),
                s.getPricePerHour(), thumbnailUrl, Boolean.TRUE.equals(s.getActive()));
    }
}
