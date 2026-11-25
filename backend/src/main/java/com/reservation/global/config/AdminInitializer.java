package com.reservation.global.config;

import com.reservation.user.entity.Role;
import com.reservation.user.entity.User;
import com.reservation.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 서버 시작 시 관리자 계정이 없으면 만든다 (시연용).
 * 비밀번호는 코드에 두지 않고 환경변수 ADMIN_PASSWORD 로만 받는다. 없으면 만들지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.login-id}")
    private String adminLoginId;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByLoginId(adminLoginId)) {
            return;
        }
        if (!StringUtils.hasText(adminPassword)) {
            log.warn("관리자 계정이 없습니다. 환경변수 ADMIN_PASSWORD 를 설정하고 다시 실행하면 '{}' 계정을 만듭니다.", adminLoginId);
            return;
        }
        userRepository.save(User.create(adminLoginId, passwordEncoder.encode(adminPassword),
                "관리자", adminLoginId + "@oneslot.local", null, Role.ADMIN));
        log.info("관리자 계정 '{}' 을 만들었습니다.", adminLoginId);
    }
}
