package com.reservation.payment.pg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * 토스페이먼츠 API 연동
 * - 승인: POST /v1/payments/confirm
 * - 취소: POST /v1/payments/{paymentKey}/cancel
 * 인증: Basic base64("시크릿키:")
 *
 * 시크릿 키는 환경 변수 TOSS_SECRET_KEY로만 넣는다. (코드·저장소에 올리지 않기)
 */
@Component
public class TossPaymentsClient implements PgClient {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentsClient.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;

    public TossPaymentsClient(
            @Value("${toss.secret-key:${TOSS_SECRET_KEY:}}") String secretKey,
            @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${toss.timeout-seconds:10}") long timeoutSeconds) {

        if (secretKey == null || secretKey.isBlank()) {
            log.warn("TOSS_SECRET_KEY가 설정되지 않았습니다. 결제 승인·취소가 실패합니다.");
        }
        String basic = Base64.getEncoder().encodeToString(
                ((secretKey == null ? "" : secretKey) + ":").getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    @Override
    public PgPaymentResult confirm(String paymentKey, String orderId, int amount) {
        return call(() -> restClient.post()
                .uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmRequest(paymentKey, orderId, amount))
                .retrieve()
                .body(TossPayment.class));
    }

    @Override
    public PgPaymentResult cancel(String paymentKey, String reason, int cancelAmount,
                                  String idempotencyKey) {
        return call(() -> restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(new CancelRequest(reason, cancelAmount))
                .retrieve()
                .body(TossPayment.class));
    }

    private PgPaymentResult call(java.util.function.Supplier<TossPayment> request) {
        try {
            TossPayment p = request.get();
            if (p == null) {
                throw new PgException("EMPTY_RESPONSE", "PG 응답이 비어 있습니다.");
            }
            return p.toResult();
        } catch (RestClientResponseException e) {
            TossError error = null;
            try {
                error = e.getResponseBodyAs(TossError.class);
            } catch (RuntimeException ignore) {
                // 응답 본문이 JSON이 아닌 경우
            }
            String code = error != null && error.code() != null ? error.code() : "HTTP_" + e.getStatusCode().value();
            String message = error != null && error.message() != null ? error.message() : "PG 요청이 거절되었습니다.";
            log.info("토스 요청 거절: code={}, message={}", code, message);
            throw new PgException(code, message);
        } catch (ResourceAccessException e) {
            log.warn("토스 응답 없음(시간 초과·네트워크): {}", e.getMessage());
            throw new PgTimeoutException("PG 응답 시간이 초과되었습니다.", e);
        }
    }

    record ConfirmRequest(String paymentKey, String orderId, int amount) {}

    record CancelRequest(String cancelReason, int cancelAmount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossError(String code, String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPayment(String paymentKey, String orderId, String status, String method,
                       Long totalAmount, String approvedAt) {

        PgPaymentResult toResult() {
            LocalDateTime approved = null;
            if (approvedAt != null && !approvedAt.isBlank()) {
                approved = OffsetDateTime.parse(approvedAt)
                        .atZoneSameInstant(SEOUL).toLocalDateTime();
            }
            return new PgPaymentResult(paymentKey, orderId, status, method,
                    totalAmount == null ? 0 : totalAmount, approved);
        }
    }
}
