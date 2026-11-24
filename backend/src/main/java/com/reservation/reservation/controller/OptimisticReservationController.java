
package com.reservation.reservation.controller;

import com.reservation.reservation.dto.ReservationCreateRequest;
import com.reservation.reservation.entity.Reservation;
import com.reservation.reservation.service.OptimisticReservationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reservations/experiments")
public class OptimisticReservationController {

    private final OptimisticReservationService optimisticReservationService;

    public OptimisticReservationController(
            OptimisticReservationService optimisticReservationService
    ) {
        this.optimisticReservationService = optimisticReservationService;
    }

    @PostMapping("/optimistic")
    public ResponseEntity<Map<String, Object>> createOptimisticReservation(
            @RequestBody ReservationCreateRequest request
    ) {
        Reservation reservation =
                optimisticReservationService.createReservation(
                        request.userId(),
                        request.spaceId(),
                        request.startAt(),
                        request.endAt()
                );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "reservationId", reservation.getId(),
                        "userId", reservation.getUserId(),
                        "spaceId", reservation.getSpace().getId(),
                        "startAt", reservation.getStartAt().toString(),
                        "endAt", reservation.getEndAt().toString(),
                        "status", reservation.getStatus().name(),
                        "lockType", "OPTIMISTIC"
                )
        );
    }
}
