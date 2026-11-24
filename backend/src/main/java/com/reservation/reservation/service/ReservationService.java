
package com.reservation.reservation.service;

import com.reservation.reservation.dto.AvailabilityResponse;
import com.reservation.reservation.entity.Reservation;
import com.reservation.reservation.entity.ReservationStatus;
import com.reservation.reservation.repository.ReservationRepository;
import com.reservation.space.entity.Space;
import com.reservation.space.repository.SpaceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

    private static final LocalTime OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(21, 0);

    private static final List<ReservationStatus> OCCUPIED_STATUSES =
            List.of(
                    ReservationStatus.PENDING_PAYMENT,
                    ReservationStatus.CONFIRMED
            );

    private final ReservationRepository reservationRepository;
    private final SpaceRepository spaceRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            SpaceRepository spaceRepository) {

        this.reservationRepository = reservationRepository;
        this.spaceRepository = spaceRepository;
    }

    // ========================================
    // 1. 예약 생성
    // ========================================

    @Transactional
    public Reservation createReservation(
            Long userId,
            Long spaceId,
            LocalDateTime startAt,
            LocalDateTime endAt) {

        if (userId == null || userId <= 0 ||
                spaceId == null || spaceId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사용자 ID와 회의실 ID가 올바르지 않습니다."
            );
        }

        validateReservationTime(startAt, endAt);

        // 동일 회의실 예약 요청을 순서대로 처리하기 위한 비관적 락
        Space space = spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회의실을 찾을 수 없습니다."
                ));

        if (!Boolean.TRUE.equals(space.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 예약할 수 없는 회의실입니다."
            );
        }

        // 이미 예약된 시간인지 확인
        List<Reservation> overlaps =
                reservationRepository.findOverlappingReservations(
                        spaceId,
                        startAt,
                        endAt,
                        OCCUPIED_STATUSES
                );

        if (!overlaps.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 예약된 시간입니다."
            );
        }

        Reservation reservation = new Reservation();

        reservation.setUserId(userId);
        reservation.setSpace(space);
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        return reservationRepository.saveAndFlush(reservation);
    }

    // ========================================
    // 2. 사용자별 예약 목록 조회
    // ========================================

    @Transactional(readOnly = true)
    public List<Reservation> getMyReservations(Long userId) {

        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사용자 ID가 올바르지 않습니다."
            );
        }

        return reservationRepository.findByUserId(userId);
    }

    // ========================================
    // 3. 예약 상세 조회
    // ========================================

    @Transactional(readOnly = true)
    public Reservation getReservation(Long reservationId) {

        if (reservationId == null || reservationId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID가 올바르지 않습니다."
            );
        }

        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "예약 정보를 찾을 수 없습니다."
                ));
    }

    // ========================================
    // 4. 캘린더 예약 가능 시간 조회
    // ========================================

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(
            Long spaceId,
            LocalDate date
    ) {
        if (spaceId == null || spaceId <= 0 || date == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "공간 ID와 날짜를 확인해 주세요."
            );
        }
    
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "공간을 찾을 수 없습니다."
                ));
    
        if (!Boolean.TRUE.equals(space.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사용할 수 없는 공간입니다."
            );
        }
    
        LocalDateTime dayStart = date.atTime(9, 0);
        LocalDateTime dayEnd = date.atTime(21, 0);
    
        List<Reservation> reservations =
                reservationRepository.findOverlappingReservations(
                        spaceId,
                        dayStart,
                        dayEnd,
                        OCCUPIED_STATUSES
                );
    
        List<AvailabilityResponse.TimeSlot> availableSlots =
                new ArrayList<>();
    
        List<AvailabilityResponse.TimeSlot> reservedSlots =
                new ArrayList<>();
    
        LocalDateTime now = LocalDateTime.now();
    
        // 09:00부터 21:00까지 10분 간격으로 확인
        LocalDateTime slotStart = dayStart;
    
        while (slotStart.isBefore(dayEnd)) {
            LocalDateTime slotEnd = slotStart.plusMinutes(10);
    
            boolean occupied = false;
    
            for (Reservation reservation : reservations) {
                // 구간이 겹치는 경우에만 예약 중
                boolean overlap =
                        reservation.getStartAt().isBefore(slotEnd)
                        && reservation.getEndAt().isAfter(slotStart);
    
                if (overlap) {
                    occupied = true;
                    break;
                }
            }
    
            AvailabilityResponse.TimeSlot slot =
                    new AvailabilityResponse.TimeSlot(
                            slotStart.toLocalTime(),
                            slotEnd.toLocalTime()
                    );
    
            if (occupied) {
                reservedSlots.add(slot);
            } else if (slotStart.isAfter(now)) {
                availableSlots.add(slot);
            }
    
            slotStart = slotEnd;
        }
    
        return new AvailabilityResponse(
                spaceId,
                date,
                availableSlots,
                reservedSlots
        );
    }
    
    // ========================================
    // 5. 예약 변경
    // ========================================

    @Transactional
    public Reservation updateReservation(
            Long reservationId,
            Long userId,
            LocalDateTime newStartAt,
            LocalDateTime newEndAt) {

        if (reservationId == null || reservationId <= 0 ||
                userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID 또는 사용자 ID가 올바르지 않습니다."
            );
        }

        validateReservationTime(newStartAt, newEndAt);

        // 변경할 예약 조회
        Reservation initialReservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "예약 정보를 찾을 수 없습니다."
                        ));

        // 본인 예약인지 확인
        if (!initialReservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인의 예약만 변경할 수 있습니다."
            );
        }

        Long spaceId = initialReservation.getSpace().getId();

        // 예약 생성과 동일한 공간 행에 비관적 락 적용
        Space space = spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회의실을 찾을 수 없습니다."
                ));

        if (!Boolean.TRUE.equals(space.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 예약할 수 없는 회의실입니다."
            );
        }

        // 락 획득 후 예약 데이터 재조회
        Reservation reservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "예약 정보를 찾을 수 없습니다."
                        ));

        if (!reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인의 예약만 변경할 수 있습니다."
            );
        }

        // 변경할 수 없는 예약 상태 검사
        if (!OCCUPIED_STATUSES.contains(reservation.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "현재 상태에서는 예약을 변경할 수 없습니다."
            );
        }

        // 자기 자신을 제외한 예약 시간 중복 검사
        List<Reservation> overlaps =
                reservationRepository
                        .findOverlappingReservationsExcludingSelf(
                                spaceId,
                                reservationId,
                                newStartAt,
                                newEndAt,
                                OCCUPIED_STATUSES
                        );

        if (!overlaps.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "변경하려는 시간에 다른 예약이 있습니다."
            );
        }

        // 예약 시간 변경
        reservation.setStartAt(newStartAt);
        reservation.setEndAt(newEndAt);

        // 변경 내용 DB 저장
        return reservationRepository.saveAndFlush(reservation);
    }

    // ========================================
    // 6. 예약 시간 공통 검증
    // ========================================

    // ========================================
    // 예약 취소
    // ========================================

    @Transactional
    public Reservation cancelReservation(
            Long reservationId,
            Long userId
    ) {
        if (reservationId == null || reservationId <= 0 ||
                userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID와 사용자 ID를 확인해 주세요."
            );
        }

        Reservation initialReservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "예약을 찾을 수 없습니다."
                        ));

        Long spaceId = initialReservation.getSpace().getId();

        // 예약 생성/변경과 동일한 공간에 비관적 락 적용
        spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "공간을 찾을 수 없습니다."
                ));

        Reservation reservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "예약을 찾을 수 없습니다."
                        ));

        if (!reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인의 예약만 취소할 수 있습니다."
            );
        }

        if (!OCCUPIED_STATUSES.contains(reservation.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 취소되었거나 취소할 수 없는 예약입니다."
            );
        }

        reservation.setStatus(ReservationStatus.CANCELLED);

        return reservationRepository.saveAndFlush(reservation);
    }

    private void validateReservationTime(
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    if (startAt == null || endAt == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "시작 시간과 종료 시간을 입력해야 합니다."
        );
    }

    if (!startAt.isBefore(endAt)) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "종료 시간은 시작 시간보다 늦어야 합니다."
        );
    }

    if (!startAt.isAfter(LocalDateTime.now())) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "지난 시간에는 예약할 수 없습니다."
        );
    }

    if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "예약은 같은 날짜 안에서만 가능합니다."
        );
    }

    boolean validStart =
            startAt.getMinute() % 10 == 0
            && startAt.getSecond() == 0
            && startAt.getNano() == 0;

    boolean validEnd =
            endAt.getMinute() % 10 == 0
            && endAt.getSecond() == 0
            && endAt.getNano() == 0;

    if (!validStart || !validEnd) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "예약 시간은 10분 단위여야 합니다."
        );
    }

    LocalTime openTime = LocalTime.of(9, 0);
    LocalTime closeTime = LocalTime.of(21, 0);

    if (
            startAt.toLocalTime().isBefore(openTime)
            || !startAt.toLocalTime().isBefore(closeTime)
            || endAt.toLocalTime().isAfter(closeTime)
    ) {
 
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "예약 가능 시간은 09:00~21:00입니다."
    );
}
} // validateReservationTime 메서드 끝

} // ReservationService 클래스 끝
