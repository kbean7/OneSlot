package com.reservation.user.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,      // 초 단위
        UserResponse user
) {
}
