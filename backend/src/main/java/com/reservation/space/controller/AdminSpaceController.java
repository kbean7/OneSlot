package com.reservation.space.controller;

import com.reservation.space.dto.*;
import com.reservation.space.service.SpaceImageService;
import com.reservation.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 관리자 공간 관리 (RSV_UI_009, 009-1). /api/admin/** 는 ADMIN 만 접근 */
@RestController
@RequestMapping("/api/admin/spaces")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final SpaceService spaceService;
    private final SpaceImageService imageService;

    // 목록 (비활성 포함). active=true/false 로 필터
    @GetMapping
    public PageResponse<SpaceSummaryResponse> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Boolean active,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return spaceService.adminList(keyword, active, page, size);
    }

    // 수정 화면에 채울 값
    @GetMapping("/{spaceId}")
    public SpaceDetailResponse detail(@PathVariable Long spaceId) {
        return spaceService.adminDetail(spaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceDetailResponse create(@Valid @RequestBody SpaceRequest request) {
        return spaceService.create(request);
    }

    @PutMapping("/{spaceId}")
    public SpaceDetailResponse update(@PathVariable Long spaceId, @Valid @RequestBody SpaceRequest request) {
        return spaceService.update(spaceId, request);
    }

    // 활성 ↔ 비활성 (삭제 대신)
    @PatchMapping("/{spaceId}/active")
    public SpaceSummaryResponse changeActive(@PathVariable Long spaceId, @Valid @RequestBody ActiveRequest request) {
        return spaceService.changeActive(spaceId, request.active());
    }

    // 사진 올리기: form-data, 키 이름 files (여러 장 가능)
    @PostMapping(value = "/{spaceId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ImageResponse> uploadImages(@PathVariable Long spaceId,
                                            @RequestParam("files") List<MultipartFile> files) {
        return imageService.upload(spaceId, files);
    }

    @DeleteMapping("/{spaceId}/images/{imageId}")
    public List<ImageResponse> deleteImage(@PathVariable Long spaceId, @PathVariable Long imageId) {
        return imageService.delete(spaceId, imageId);
    }

    // 사진 순서 변경 (첫 번째가 대표 사진)
    @PutMapping("/{spaceId}/images/order")
    public List<ImageResponse> reorderImages(@PathVariable Long spaceId, @Valid @RequestBody ImageOrderRequest request) {
        return imageService.reorder(spaceId, request.imageIds());
    }
}
