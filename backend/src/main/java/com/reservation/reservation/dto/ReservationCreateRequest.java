
package com.reservation.reservation.dto;

import java.time.LocalDateTime;

public record ReservationCreateRequest(
        Long userId,
        Long spaceId,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
