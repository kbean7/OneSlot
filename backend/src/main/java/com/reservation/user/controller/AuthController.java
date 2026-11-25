package com.reservation.user.controller;

import com.reservation.user.dto.LoginRequest;
import com.reservation.user.dto.LoginResponse;
import com.reservation.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 로그인 (RSV_UI_003). JWT 는 상태를 서버에 두지 않으므로 로그아웃은 프론트에서 토큰을 지우면 끝
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
