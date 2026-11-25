package com.reservation.global.security;

import com.reservation.user.entity.Role;

/**
 * JWT 에서 꺼낸 로그인 사용자.
 * 컨트롤러에서 {@code @AuthenticationPrincipal LoginUser loginUser} 로 받아 loginUser.id() 를 쓴다.
 * 로그인하지 않은 요청이면 null 이 들어온다.
 */
public record LoginUser(Long id, String loginId, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
