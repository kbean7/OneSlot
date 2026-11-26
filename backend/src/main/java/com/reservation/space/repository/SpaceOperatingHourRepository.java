package com.reservation.space.repository;

import com.reservation.space.entity.SpaceOperatingHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpaceOperatingHourRepository extends JpaRepository<SpaceOperatingHour, Long> {

    List<SpaceOperatingHour> findBySpaceIdOrderByDayOfWeekAsc(Long spaceId);

    List<SpaceOperatingHour> findBySpaceIdIn(Collection<Long> spaceIds);

    Optional<SpaceOperatingHour> findBySpaceIdAndDayOfWeek(Long spaceId, Integer dayOfWeek);
}
