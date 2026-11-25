package com.reservation.user.dto;

import com.reservation.user.entity.User;

public record UserResponse(
        Long id,
        String loginId,
        String name,
        String email,
        String phone,
        String role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLoginId(), user.getName(),
                user.getEmail(), user.getPhone(), user.getRole().name());
    }
}
