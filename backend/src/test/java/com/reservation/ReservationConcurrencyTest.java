
package com.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = ReservationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ReservationConcurrencyTest {

    @LocalServerPort
    private int port;

    @Test
    void simultaneousReservationsOnlyOneSucceeds() throws Exception {

        int requestCount = 10;

        // 테스트 전 해당 시간에 기존 예약이 없는지 확인
        String startAt = "2026-12-27T09:00:00";
        String endAt = "2026-12-27T10:10:00";

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch ready =
                new CountDownLatch(requestCount);

        CountDownLatch start =
                new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 1; i <= requestCount; i++) {
                final int userId = i;

                futures.add(executor.submit(() -> {

                    String json = String.format(
                        "{\"userId\":%d,\"spaceId\":1,"
                        + "\"startAt\":\"%s\","
                        + "\"endAt\":\"%s\"}",
                        userId, startAt, endAt
                    );

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(
                                "http://localhost:" + port
                                + "/api/reservations"
                            ))
                            .header(
                                "Content-Type",
                                "application/json"
                            )
                            .timeout(Duration.ofSeconds(20))
                            .POST(
                                HttpRequest.BodyPublishers
                                    .ofString(json)
                            )
                            .build();

                    ready.countDown();
                    start.await();

                    HttpResponse<String> response =
                            client.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                            );

                    int status = response.statusCode();

                    System.out.println(
                        "User " + userId + " -> HTTP " + status
                    );

                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        throw new AssertionError(
                            "Unexpected HTTP " + status
                            + ": " + response.body()
                        );
                    }

                    return null;
                }));
            }

            assertTrue(
                ready.await(10, TimeUnit.SECONDS),
                "요청 스레드 준비 실패"
            );

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            System.out.println(
                "SUCCESS = " + successCount.get()
            );

            System.out.println(
                "CONFLICT = " + conflictCount.get()
            );

            assertEquals(1, successCount.get());
            assertEquals(9, conflictCount.get());

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}