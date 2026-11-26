package com.reservation.space.dto;

import com.reservation.space.entity.SpaceImage;

public record ImageResponse(Long id, String url, Integer sortOrder) {

    public static ImageResponse from(SpaceImage image) {
        return new ImageResponse(image.getId(), "/uploads/" + image.getFilePath(), image.getSortOrder());
    }
}
