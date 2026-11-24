
package com.reservation.reservation.dto;

import java.time.LocalDateTime;

public record ReservationUpdateRequest(
        Long userId,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
