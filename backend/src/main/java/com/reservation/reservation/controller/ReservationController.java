
package com.reservation.reservation.controller;

import com.reservation.reservation.dto.AvailabilityResponse;
import com.reservation.reservation.dto.ReservationCreateRequest;
import com.reservation.reservation.dto.ReservationUpdateRequest;
import com.reservation.reservation.entity.Reservation;
import com.reservation.reservation.service.ReservationService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;
import com.reservation.reservation.entity.Reservation;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(
            ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // 1. 예약 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody ReservationCreateRequest request) {

        Reservation r = reservationService.createReservation(
                request.userId(),
                request.spaceId(),
                request.startAt(),
                request.endAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "reservationId", r.getId(),
                        "spaceId", r.getSpace().getId(),
                        "status", r.getStatus().name()
                )
        );
    }

    // 2. 사용자별 예약 목록 조회
    @GetMapping("/users/{userId}")
    public List<Map<String, Object>> getMyReservations(
            @PathVariable Long userId) {

        return reservationService.getMyReservations(userId)
                .stream()
                .map(r -> Map.<String, Object>of(
                        "reservationId", r.getId(),
                        "spaceId", r.getSpace().getId(),
                        "startAt", r.getStartAt().toString(),
                        "endAt", r.getEndAt().toString(),
                        "status", r.getStatus().name()
                ))
                .toList();
    }

    // 3. 캘린더 예약 가능 시간 조회
    @GetMapping("/spaces/{spaceId}/availability")
    public AvailabilityResponse getAvailability(
            @PathVariable Long spaceId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return reservationService.getAvailability(spaceId, date);
    }

    // 4. 예약 상세 조회
    @GetMapping("/{reservationId}")
    public Map<String, Object> getReservation(
            @PathVariable Long reservationId) {

        Reservation r = reservationService.getReservation(reservationId);

        return Map.of(
                "reservationId", r.getId(),
                "userId", r.getUserId(),
                "spaceId", r.getSpace().getId(),
                "startAt", r.getStartAt().toString(),
                "endAt", r.getEndAt().toString(),
                "status", r.getStatus().name()
        );
    }

    // 5. 예약 변경
    @PatchMapping("/{reservationId}")
    public Map<String, Object> updateReservation(
            @PathVariable Long reservationId,
            @RequestBody ReservationUpdateRequest request) {

        Reservation r = reservationService.updateReservation(
                reservationId,
                request.userId(),
                request.startAt(),
                request.endAt()
        );

        return Map.of(
                "reservationId", r.getId(),
                "spaceId", r.getSpace().getId(),
                "startAt", r.getStartAt().toString(),
                "endAt", r.getEndAt().toString(),
                "status", r.getStatus().name()
        );
    }
    
    // 예약 취소
    @PatchMapping("/{reservationId}/cancel")
    public Map<String, Object> cancelReservation(
            @PathVariable Long reservationId,
            @RequestParam Long userId
    ) {
        Reservation reservation =
                reservationService.cancelReservation(
                        reservationId,
                        userId
                );

        return Map.of(
                "reservationId", reservation.getId(),
                "userId", reservation.getUserId(),
                "spaceId", reservation.getSpace().getId(),
                "status", reservation.getStatus().name(),
                "message", "예약이 취소되었습니다."
        );
    }

}
