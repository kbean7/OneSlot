-- 결제 기능을 예약 코드 없이 로컬에서 실행해 볼 때만 사용 (MariaDB, reservation_db)
-- 예약 코드가 합쳐지면 spaces·reservations는 예약 쪽 엔티티가 만들므로 이 파일은 필요 없음
-- payments·refunds 테이블은 서버를 켜면 자동으로 생성됨 (ddl-auto=update)

CREATE TABLE IF NOT EXISTS spaces (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    capacity  INT NOT NULL,
    location  VARCHAR(100),
    active    BOOLEAN NOT NULL DEFAULT TRUE,
    version   BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS reservations (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    space_id     BIGINT NOT NULL,
    start_at     DATETIME(6) NOT NULL,
    end_at       DATETIME(6) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    expires_at   DATETIME(6),
    total_price  INT
);

-- 테스트 데이터: 사용자 1의 결제 대기 예약 (3일 뒤 이용, 결제 마감 1시간 뒤, 30,000원)
INSERT INTO spaces (name, capacity, location) VALUES ('회의실A', 4, '서울');
INSERT INTO reservations (user_id, space_id, start_at, end_at, status, created_at, updated_at, expires_at, total_price)
VALUES (1, LAST_INSERT_ID(), NOW() + INTERVAL 3 DAY, NOW() + INTERVAL 3 DAY + INTERVAL 1 HOUR,
        'PENDING_PAYMENT', NOW(), NOW(), NOW() + INTERVAL 1 HOUR, 30000);
