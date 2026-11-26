# 공간·운영 시간 API

담당: 박경빈 · 화면: RSV_UI_002 메인, 005 공간 목록·검색, 006 공간 상세(정보 영역), 009 관리자 공간 관리, 009-1 공간 등록·수정

## 사용자 API (로그인 없이)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/spaces?date&start&end&people&location&sort&page&size` | 검색. 모든 조건 선택. `start`·`end`는 `10:00` 형식, 10분 단위 |
| GET | `/api/spaces/popular?limit=3` | 메인 인기 공간 (최근 30일 확정·이용완료 예약 수) |
| GET | `/api/spaces/{id}` | 상세 (사진, 요일별 운영 시간 포함). 비활성 공간은 404 |
| GET | `/api/spaces/{id}/hours?date=2026-12-01` | 그날 운영 시간 `{ openTime, closeTime, closed }` |

- 검색에서 시간 조건이 있으면 **그 시간에 운영하고 예약이 겹치지 않는 활성 공간만** 나와요. 날짜만 있으면 그날 휴무인 공간만 빠져요.
- 겹침 판단: 확정(CONFIRMED) 예약 + **결제 마감 전인** 결제대기(PENDING_PAYMENT) 예약.
- `sort`: `price_asc` 낮은 가격순 · `price_desc` 높은 가격순 · `capacity` 수용 인원순 · 없으면 최신순
- `page`는 0부터, 응답은 `{ content, page, size, totalElements, totalPages }`
- 사진 주소는 `/uploads/spaces/xxx.png` 형태로 내려가요. 프론트에서 `<img src={url}>`로 그대로 쓰면 돼요. (Vite 프록시에 `/uploads`도 추가 필요)

## 관리자 API (`/api/admin/**`, ADMIN 토큰 필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/spaces?keyword&active&page&size` | 목록 (비활성 포함) |
| GET | `/api/admin/spaces/{id}` | 수정 화면에 채울 값 |
| POST | `/api/admin/spaces` | 등록. `operatingHours`를 비우면 월~일 09:00~21:00 |
| PUT | `/api/admin/spaces/{id}` | 수정 |
| PATCH | `/api/admin/spaces/{id}/active` | `{ "active": false }` 비활성(삭제 대신) |
| POST | `/api/admin/spaces/{id}/images` | 사진 올리기 (form-data `files`, jpg·png, 장당 5MB, 최대 5장) |
| DELETE | `/api/admin/spaces/{id}/images/{imageId}` | 사진 삭제 |
| PUT | `/api/admin/spaces/{id}/images/order` | `{ "imageIds": [3,1,2] }` 첫 번째가 대표 사진 |

운영 시간은 7개 요일을 모두 보내요. `dayOfWeek` 1=월 … 7=일, 휴무는 `{ "dayOfWeek": 7, "closed": true }`.

## 예약 파트(호현)에서 바꿀 것

`ReservationService`의 09:00~21:00 고정 대신 `SpaceService`를 주입해서 씁니다.

```java
private final SpaceService spaceService;

// 예약 생성·변경 시 (validateBusinessHours 대신)
spaceService.validateWithinOperatingHours(spaceId, startAt, endAt);   // 벗어나면 400

// 캘린더(availability)에서 그날 칸을 그릴 범위
OperatingWindow w = spaceService.getOperatingWindow(spaceId, date);
if (w.closed()) { /* 그날은 전부 예약 불가 */ }
LocalDateTime dayStart = date.atTime(w.open());
LocalDateTime dayEnd   = date.atTime(w.close());
```

운영 시간을 등록하지 않은 기존 공간은 지금과 똑같이 09:00~21:00으로 동작해요.
