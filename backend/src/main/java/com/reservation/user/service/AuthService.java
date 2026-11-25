package com.reservation.user.service;

import com.reservation.global.security.JwtTokenProvider;
import com.reservation.user.dto.LoginRequest;
import com.reservation.user.dto.LoginResponse;
import com.reservation.user.dto.UserResponse;
import com.reservation.user.entity.User;
import com.reservation.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    // 아이디가 틀렸는지 비밀번호가 틀렸는지 알려 주지 않는다 (화면설계서 RSV_UI_003 ④)
    private static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword()) || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED);
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getLoginId(), user.getRole());
        return new LoginResponse(token, "Bearer", jwtTokenProvider.getExpirationSeconds(), UserResponse.from(user));
    }
}
