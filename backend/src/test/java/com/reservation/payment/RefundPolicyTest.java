package com.reservation.payment;

import com.reservation.payment.service.RefundPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundPolicyTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 5, 10, 0);

    private final RefundPolicy policy =
            new RefundPolicy(Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @Test
    void 이용_24시간_이상_전이면_전액_환불() {
        var q = policy.quote(30000, NOW.plusHours(25));
        assertEquals(100, q.rate());
        assertEquals(30000, q.amount());
    }

    @Test
    void 정확히_24시간_전이면_전액_환불() {
        assertEquals(100, policy.quote(30000, NOW.plusHours(24)).rate());
    }

    @Test
    void 이용_24시간_미만_전이면_50퍼센트_환불() {
        var q = policy.quote(30000, NOW.plusHours(23).plusMinutes(59));
        assertEquals(50, q.rate());
        assertEquals(15000, q.amount());
    }

    @Test
    void 홀수_금액은_원단위_내림() {
        assertEquals(7500, policy.quote(15001, NOW.plusHours(1)).amount());
    }

    @Test
    void 이용_시작_이후면_409() {
        var e1 = assertThrows(ResponseStatusException.class, () -> policy.quote(30000, NOW));
        assertEquals(409, e1.getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> policy.quote(30000, NOW.minusMinutes(1)));
    }

    @Test
    void 결제_금액이_0이하면_예외() {
        assertThrows(IllegalArgumentException.class, () -> policy.quote(0, NOW.plusDays(2)));
    }
}
