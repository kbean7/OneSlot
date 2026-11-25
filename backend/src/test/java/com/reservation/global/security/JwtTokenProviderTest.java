package com.reservation.global.security;

import com.reservation.user.entity.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DB·스프링 없이 도는 단위 테스트
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-oneslot-0123456789";

    @Test
    void 토큰을_만들고_다시_읽으면_같은_사용자() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60);

        String token = provider.createToken(7L, "kbean", Role.ADMIN);

        LoginUser user = provider.parse(token).orElseThrow();
        assertThat(user.id()).isEqualTo(7L);
        assertThat(user.loginId()).isEqualTo("kbean");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void 다른_키로_서명된_토큰은_거부() {
        String token = new JwtTokenProvider(SECRET, 60).createToken(1L, "a", Role.USER);
        JwtTokenProvider other = new JwtTokenProvider("another-secret-key-for-oneslot-99999", 60);

        assertThat(other.parse(token)).isEmpty();
    }

    @Test
    void 만료된_토큰은_거부() {
        JwtTokenProvider expired = new JwtTokenProvider(SECRET, -1);   // 1분 전에 만료

        assertThat(expired.parse(expired.createToken(1L, "a", Role.USER))).isEmpty();
    }

    @Test
    void 망가진_토큰은_거부() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60);

        assertThat(provider.parse("not.a.token")).isEmpty();
        assertThat(provider.parse("")).isEmpty();
    }

    @Test
    void 짧은_키는_시작부터_막는다() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
