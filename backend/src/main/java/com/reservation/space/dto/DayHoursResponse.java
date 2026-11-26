package com.reservation.space.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** 특정 날짜의 운영 시간 (예약 캘린더가 칸을 그릴 범위) */
public record DayHoursResponse(LocalDate date, LocalTime openTime, LocalTime closeTime, boolean closed) {
}
