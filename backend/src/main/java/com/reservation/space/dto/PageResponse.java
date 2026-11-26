package com.reservation.space.dto;

import java.util.List;

/** page 는 0부터. 화면에 보여 줄 때는 +1 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /** 공간 수가 많지 않아 메모리에서 자른다 (수백 개 이하 기준) */
    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = (all.size() + safeSize - 1) / safeSize;
        return new PageResponse<>(List.copyOf(all.subList(from, to)), safePage, safeSize, all.size(), totalPages);
    }
}
