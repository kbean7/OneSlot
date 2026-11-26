package com.reservation.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class PaymentConfig {

    /** 현재 시각. 테스트에서 고정 시각으로 바꿀 수 있도록 Bean으로 둔다. */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    /** PG 호출을 트랜잭션 밖에 두기 위해 단계별로 트랜잭션을 나눌 때 사용 */
    @Bean
    public TransactionTemplate paymentTransactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
