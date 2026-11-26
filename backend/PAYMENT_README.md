# 결제·환불 모듈 (담당: 양가연)

패키지: `com.reservation.payment` — 예약 코드(`com.reservation.reservation`)를 import하지 않는다.
예약 데이터는 `ReservationPort` → `JdbcReservationAdapter`(reservations 테이블 SQL)로만 접근한다.

## 실행 전 준비

1. 토스페이먼츠 개발자센터에서 테스트 키 발급
2. 환경 변수 설정 (저장소에 올리지 않기)
   ```
   TOSS_SECRET_KEY=test_sk_xxxxxxxx   # 서버용
   ```
   프론트에는 클라이언트 키(`test_ck_...`)만 넣는다.
3. 예약 테이블 준비
   - 예약 코드가 아직 없으면: `payment-dev-schema.sql`을 MariaDB(reservation_db)에서 실행 (테스트 예약 1건 포함)
   - 예약 코드가 합쳐진 뒤에는 금액 컬럼만 추가
     ```sql
     ALTER TABLE reservations ADD COLUMN total_price INT NULL;
     ```
   `payments`, `refunds` 테이블은 `ddl-auto=update`로 자동 생성된다.

## API

| 메서드 | URL | 설명 | 주요 오류 |
|---|---|---|---|
| POST | `/api/payments` | 결제 요청. body `{reservationId, userId}` → 주문번호·금액·남은 시간 | 403, 404, 409(확정됨·마감·금액 없음·승인 중) |
| POST | `/api/payments/confirm` | 결제창 성공 후 승인. body `{paymentKey, orderId, amount, userId}` | 400(금액 불일치·PG 거절), 409, 504 |
| GET | `/api/payments/users/{userId}` | 결제·환불 내역 | |
| GET | `/api/payments/reservations/{id}/refund-quote?userId=` | 예상 환불률·금액 | 403, 409(이용 시작 후) |
| POST | `/api/payments/reservations/{id}/cancel` | 예약 취소 + 환불. body `{userId, reason}` | 403, 409(중복·이용 시작 후), 502, 504 |

오류 응답: `{ "code": 409, "message": "...", "timestamp": "..." }`

## 결제 흐름 (토스)

```
React ── POST /api/payments ──────────────▶ 서버: READY 저장, orderId 발급
React ── 토스 결제창(requestPayment) ─────▶ 토스
토스 ── successUrl?paymentKey&orderId&amount ▶ React
React ── POST /api/payments/confirm ─────▶ 서버: 검증 → IN_PROGRESS → 토스 승인 API → PAID + 예약 CONFIRMED
```

## 동시성·정합성 처리

- 락 순서: 예약 행 → 결제 행 (요청·승인·취소·만료 모두 같은 순서)
- 승인: PG 호출은 트랜잭션 밖. 예약 확정은 `PENDING_PAYMENT일 때만` 조건부 UPDATE
  - 승인 도중 예약이 만료되면 PG 결제 자동 취소 후 409
- 같은 orderId 재승인: PG 호출 없이 기존 결과 반환
- 만료 스케줄러(1분): IN_PROGRESS 결제가 있는 예약은 건너뜀, 1분 넘게 멈춘 IN_PROGRESS는 FAILED
- 환불: 예약 행을 잠근 채 처리 → 동시 취소해도 환불 1건, `refunds.payment_id` 유니크가 최종 방어선
- 토스 취소 요청에 `Idempotency-Key` 사용

## 테스트

```
./gradlew test --tests "com.reservation.payment.*"     (Windows: .\gradlew.bat test)
```
H2 인메모리 DB + 가짜 PG(`FakePgClient`)로 실행되어 MariaDB·토스 키 없이 돌아간다.

## 예약 쪽과 맞출 것 (연결 지점은 `JdbcReservationAdapter` 한 파일)

- `reservations.total_price` 컬럼
- 예약 상태 문자열 (`ReservationState`): 예약 쪽 철자가 바뀌면 여기만 수정
- 기존 `PATCH /api/reservations/{id}/cancel`은 환불 없이 취소한다. 결제된 예약 취소는 결제 쪽 cancel API를 쓰도록 맞춰야 함
