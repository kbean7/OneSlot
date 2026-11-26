package com.reservation.space.dto;

import jakarta.validation.constraints.NotNull;

public record ActiveRequest(@NotNull(message = "active 값이 필요합니다.") Boolean active) {
}
