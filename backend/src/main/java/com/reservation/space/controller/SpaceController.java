package com.reservation.space.controller;

import com.reservation.space.dto.DayHoursResponse;
import com.reservation.space.dto.PageResponse;
import com.reservation.space.dto.SpaceDetailResponse;
import com.reservation.space.dto.SpaceSummaryResponse;
import com.reservation.space.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 사용자 화면용 공간 조회. 로그인 없이 호출 가능 */
@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    // 공간 목록·검색 (RSV_UI_005)
    // 예) /api/spaces?date=2026-12-01&start=10:00&end=12:00&people=4&location=강남&sort=price_asc&page=0
    @GetMapping
    public PageResponse<SpaceSummaryResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime end,
            @RequestParam(required = false) Integer people,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return spaceService.search(date, start, end, people, location, sort, page, size);
    }

    // 메인 인기 공간 (RSV_UI_002)
    @GetMapping("/popular")
    public List<SpaceSummaryResponse> popular(@RequestParam(defaultValue = "3") int limit) {
        return spaceService.popular(Math.max(1, Math.min(limit, 12)));
    }

    // 공간 상세 (RSV_UI_006)
    @GetMapping("/{spaceId}")
    public SpaceDetailResponse detail(@PathVariable Long spaceId) {
        return spaceService.getActiveDetail(spaceId);
    }

    // 특정 날짜 운영 시간 (예약 캘린더가 그릴 범위)
    @GetMapping("/{spaceId}/hours")
    public DayHoursResponse hours(@PathVariable Long spaceId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return spaceService.getDayHours(spaceId, date);
    }
}
