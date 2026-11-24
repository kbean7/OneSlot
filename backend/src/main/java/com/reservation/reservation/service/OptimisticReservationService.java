
package com.reservation.reservation.service;

import com.reservation.reservation.entity.Reservation;
import com.reservation.reservation.entity.ReservationStatus;
import com.reservation.reservation.repository.ReservationRepository;
import com.reservation.space.entity.Space;
import com.reservation.space.repository.SpaceRepository;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class OptimisticReservationService {

    private static final List<ReservationStatus> OCCUPIED =
            List.of(
                    ReservationStatus.PENDING_PAYMENT,
                    ReservationStatus.CONFIRMED
            );

    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final TransactionTemplate transactionTemplate;

    public OptimisticReservationService(
            SpaceRepository spaceRepository,
            ReservationRepository reservationRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.spaceRepository = spaceRepository;
        this.reservationRepository = reservationRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public Reservation createReservation(
            Long userId,
            Long spaceId,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        validate(userId, spaceId, startAt, endAt);

        try {
            // 트랜잭션 커밋까지 이 블록 안에서 수행
            return transactionTemplate.execute(status -> {

                // Space의 version을 이용해 낙관적 락 적용
                Space space = spaceRepository
                        .findByIdForOptimisticUpdate(spaceId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "공간을 찾을 수 없습니다."
                        ));

                if (!Boolean.TRUE.equals(space.getActive())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "예약할 수 없는 공간입니다."
                    );
                }

                List<Reservation> overlaps =
                        reservationRepository
                                .findOverlappingReservations(
                                        spaceId,
                                        startAt,
                                        endAt,
                                        OCCUPIED
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
                reservation.setStatus(
                        ReservationStatus.PENDING_PAYMENT
                );
                reservation.setExpiresAt(
                        LocalDateTime.now().plusMinutes(10)
                );

                // 예약 저장
                return reservationRepository.saveAndFlush(
                        reservation
                );
            });

        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "동시에 다른 예약이 처리되었습니다.",
                    e
            );
        } catch (CannotAcquireLockException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "DB 잠금 충돌로 예약에 실패했습니다.",
                    e
            );
        }
    }

    private void validate(
            Long userId,
            Long spaceId,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        if (userId == null || userId <= 0 ||
                spaceId == null || spaceId <= 0 ||
                startAt == null || endAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 정보를 확인해 주세요."
            );
        }

        if (!startAt.isBefore(endAt) ||
                !startAt.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 시간이 올바르지 않습니다."
            );
        }

        if (!startAt.toLocalDate().equals(
                endAt.toLocalDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "같은 날짜 안에서 예약해야 합니다."
            );
        }

        if (startAt.getMinute() % 10 != 0 ||
                endAt.getMinute() % 10 != 0 ||
                startAt.getSecond() != 0 ||
                endAt.getSecond() != 0 ||
                startAt.getNano() != 0 ||
                endAt.getNano() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "10분 단위로 예약해야 합니다."
            );
        }

        LocalTime open = LocalTime.of(9, 0);
        LocalTime close = LocalTime.of(21, 0);

        if (startAt.toLocalTime().isBefore(open) ||
                !startAt.toLocalTime().isBefore(close) ||
                endAt.toLocalTime().isAfter(close)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "운영 시간은 09:00~21:00입니다."
            );
        }
    }
}
