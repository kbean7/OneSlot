# 회원·로그인(JWT) 사용법

담당: 박경빈 · 화면: RSV_UI_003 로그인, RSV_UI_004 회원가입

## API

| 메서드 | 경로 | 로그인 | 설명 |
|---|---|---|---|
| POST | `/api/users` | X | 회원가입 → 201 + 회원 정보 |
| GET | `/api/users/check-id?loginId=` | X | 아이디 중복확인 → `{ loginId, available }` |
| POST | `/api/auth/login` | X | 로그인 → `{ accessToken, tokenType, expiresIn, user }` |
| GET | `/api/users/me` | O | 내 정보 |

로그아웃 API는 없습니다. JWT는 서버에 상태가 없어서 프론트에서 토큰을 지우면 로그아웃입니다.
토큰 유효시간은 120분(`jwt.expiration-minutes`)입니다.

## 오류 응답 (모든 API 공통)

```json
{ "status": 409, "error": "CONFLICT", "message": "이미 사용 중인 아이디입니다.", "errors": {} }
```

- 서비스에서 `throw new ResponseStatusException(HttpStatus.CONFLICT, "메시지")` 하면 위 형식으로 나갑니다. (예약 코드도 그대로 적용됨)
- `@Valid` 검증 실패는 400이고, `errors`에 `{ "필드명": "메시지" }`가 담깁니다.
- 프론트는 `message`를 그대로 화면에 띄우면 됩니다.
- 토큰 없음·만료 → 401 `로그인이 필요합니다.` / 권한 없음 → 403 `접근 권한이 없습니다.`

## 백엔드: 로그인 사용자 꺼내기 (예약·결제 파트)

요청 바디나 경로로 받던 `userId` 대신 토큰에서 꺼냅니다.

```java
@PostMapping
public ResponseEntity<?> create(@AuthenticationPrincipal LoginUser loginUser,
                                @RequestBody ReservationCreateRequest request) {
    Long userId = loginUser.id();
    ...
}
```

전환이 끝나면 `SecurityConfig`의 임시 허용 두 줄(`/api/reservations/**`, `/api/payments/**`)을 지워 주세요.
관리자 API는 `/api/admin/**` 아래에 두면 ADMIN만 접근됩니다.

## 프론트: 토큰 보내기

```js
// 로그인 성공 시
localStorage.setItem("accessToken", data.accessToken);

// 모든 API 요청에
const token = localStorage.getItem("accessToken");
fetch(`/api${path}`, {
  ...options,
  headers: { "Content-Type": "application/json",
             ...(token && { Authorization: `Bearer ${token}` }) },
});
// 401 이 오면 토큰을 지우고 로그인 화면으로
```

## 로컬 실행 환경변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | O | 기존과 같음 |
| `JWT_SECRET` | 시연 때 | 32자 이상. 없으면 로컬 개발용 기본값 사용 |
| `ADMIN_PASSWORD` | 선택 | 있으면 서버 시작 시 `admin` 관리자 계정을 만듦 |

`http/auth.http`로 바로 호출해 볼 수 있습니다.
