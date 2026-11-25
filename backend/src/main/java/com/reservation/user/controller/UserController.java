package com.reservation.user.controller;

import com.reservation.global.security.LoginUser;
import com.reservation.user.dto.CheckIdResponse;
import com.reservation.user.dto.SignupRequest;
import com.reservation.user.dto.UserResponse;
import com.reservation.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 회원가입 (RSV_UI_004)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return userService.signup(request);
    }

    // 아이디 중복확인 (RSV_UI_004 ①)
    @GetMapping("/check-id")
    public CheckIdResponse checkId(@RequestParam String loginId) {
        return new CheckIdResponse(loginId, userService.isLoginIdAvailable(loginId));
    }

    // 내 정보 (GNB 이름 표시, 새로고침 후 로그인 상태 복원)
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal LoginUser loginUser) {
        return userService.getUser(loginUser.id());
    }
}
