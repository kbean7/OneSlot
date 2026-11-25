package com.reservation.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디
    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String loginId;

    // BCrypt 로 암호화된 비밀번호
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    // 예약 알림을 받는 이메일
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static User create(String loginId, String encodedPassword, String name,
                              String email, String phone, Role role) {
        User user = new User();
        user.loginId = loginId;
        user.password = encodedPassword;
        user.name = name;
        user.email = email;
        user.phone = phone;
        user.role = role;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
