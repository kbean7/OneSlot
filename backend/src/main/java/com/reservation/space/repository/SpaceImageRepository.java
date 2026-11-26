package com.reservation.space.repository;

import com.reservation.space.entity.SpaceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpaceImageRepository extends JpaRepository<SpaceImage, Long> {

    List<SpaceImage> findBySpaceIdOrderBySortOrderAsc(Long spaceId);

    List<SpaceImage> findBySpaceIdInOrderBySortOrderAsc(Collection<Long> spaceIds);

    long countBySpaceId(Long spaceId);
}
