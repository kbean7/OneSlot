package com.reservation.payment;

import com.reservation.payment.pg.PgClient;
import com.reservation.payment.pg.PgException;
import com.reservation.payment.pg.PgPaymentResult;
import com.reservation.payment.pg.PgTimeoutException;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 가짜 PG. 승인·취소 결과와 지연을 조절할 수 있다. */
public class FakePgClient implements PgClient {

    public enum Behavior { OK, REJECT, TIMEOUT }

    public volatile Behavior confirmBehavior = Behavior.OK;
    public volatile Behavior cancelBehavior = Behavior.OK;
    public volatile long confirmDelayMillis = 0;
    public volatile Runnable duringConfirm = () -> { };

    public final AtomicInteger confirmCalls = new AtomicInteger();
    public final AtomicInteger cancelCalls = new AtomicInteger();
    public volatile Integer lastCancelAmount;

    public void reset() {
        confirmBehavior = Behavior.OK;
        cancelBehavior = Behavior.OK;
        confirmDelayMillis = 0;
        duringConfirm = () -> { };
        confirmCalls.set(0);
        cancelCalls.set(0);
        lastCancelAmount = null;
    }

    @Override
    public PgPaymentResult confirm(String paymentKey, String orderId, int amount) {
        confirmCalls.incrementAndGet();
        sleep(confirmDelayMillis);
        duringConfirm.run();
        return switch (confirmBehavior) {
            case OK -> new PgPaymentResult(paymentKey, orderId, "DONE", "카드", amount, LocalDateTime.now());
            case REJECT -> throw new PgException("REJECT_CARD_PAYMENT", "한도 초과");
            case TIMEOUT -> throw new PgTimeoutException("timeout", null);
        };
    }

    @Override
    public PgPaymentResult cancel(String paymentKey, String reason, int cancelAmount, String idempotencyKey) {
        cancelCalls.incrementAndGet();
        lastCancelAmount = cancelAmount;
        return switch (cancelBehavior) {
            case OK -> new PgPaymentResult(paymentKey, null, "CANCELED", "카드", cancelAmount, null);
            case REJECT -> throw new PgException("NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제");
            case TIMEOUT -> throw new PgTimeoutException("timeout", null);
        };
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
