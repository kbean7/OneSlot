package com.reservation.space.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 사진 순서 변경: 첫 번째가 대표 사진 */
public record ImageOrderRequest(@NotEmpty(message = "사진 순서가 필요합니다.") List<Long> imageIds) {
}
