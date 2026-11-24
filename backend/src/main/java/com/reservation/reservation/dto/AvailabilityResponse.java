
package com.reservation.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityResponse(
        Long spaceId,
        LocalDate date,
        List<TimeSlot> availableSlots,
        List<TimeSlot> reservedSlots
) {
    public record TimeSlot(
            LocalTime start,
            LocalTime end
    ) {
    }
}
