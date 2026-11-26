package com.reservation.space.service;

import com.reservation.space.dto.ImageResponse;
import com.reservation.space.entity.Space;
import com.reservation.space.entity.SpaceImage;
import com.reservation.space.repository.SpaceImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 공간 사진: 서버의 업로드 폴더(app.upload-dir)에 저장하고 /uploads/** 로 보여 준다.
 * jpg·png, 장당 5MB 이하, 공간당 최대 5장. sort_order 0 이 대표 사진.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SpaceImageService {

    private static final int MAX_IMAGES = 5;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png");

    private final SpaceImageRepository imageRepository;
    private final SpaceService spaceService;
    private final Path root;

    public SpaceImageService(SpaceImageRepository imageRepository, SpaceService spaceService,
                             @Value("${app.upload-dir}") String uploadDir) {
        this.imageRepository = imageRepository;
        this.spaceService = spaceService;
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public List<ImageResponse> upload(Long spaceId, List<MultipartFile> files) {
        Space space = spaceService.findSpace(spaceId);
        List<MultipartFile> valid = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (valid.isEmpty()) {
            throw badRequest("업로드할 사진을 선택하세요.");
        }
        long current = imageRepository.countBySpaceId(spaceId);
        if (current + valid.size() > MAX_IMAGES) {
            throw badRequest("사진은 공간당 최대 " + MAX_IMAGES + "장입니다. (현재 " + current + "장)");
        }
        valid.forEach(this::check);

        int order = (int) current;
        for (MultipartFile file : valid) {
            String fileName = "spaces/" + UUID.randomUUID() + "." + ALLOWED.get(file.getContentType());
            store(file, fileName);
            imageRepository.save(SpaceImage.create(space, fileName, file.getOriginalFilename(), order++));
        }
        return list(spaceId);
    }

    @Transactional
    public List<ImageResponse> delete(Long spaceId, Long imageId) {
        SpaceImage image = imageRepository.findById(imageId)
                .filter(i -> i.getSpace().getId().equals(spaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
        imageRepository.delete(image);
        try {
            Files.deleteIfExists(root.resolve(image.getFilePath()));
        } catch (IOException e) {
            log.warn("사진 파일 삭제 실패: {}", image.getFilePath(), e);
        }
        // 남은 사진 순서를 0부터 다시 매긴다
        List<SpaceImage> rest = new ArrayList<>(imageRepository.findBySpaceIdOrderBySortOrderAsc(spaceId));
        rest.removeIf(i -> i.getId().equals(imageId));
        for (int i = 0; i < rest.size(); i++) {
            rest.get(i).changeSortOrder(i);
        }
        return list(spaceId);
    }

    /** imageIds 순서대로 sort_order 를 다시 매긴다. 첫 번째가 대표 사진 */
    @Transactional
    public List<ImageResponse> reorder(Long spaceId, List<Long> imageIds) {
        List<SpaceImage> images = imageRepository.findBySpaceIdOrderBySortOrderAsc(spaceId);
        Set<Long> owned = new HashSet<>();
        images.forEach(i -> owned.add(i.getId()));
        if (imageIds.size() != images.size() || !owned.equals(new HashSet<>(imageIds))) {
            throw badRequest("이 공간의 사진 ID 를 빠짐없이 한 번씩 보내 주세요.");
        }
        Map<Long, SpaceImage> byId = new HashMap<>();
        images.forEach(i -> byId.put(i.getId(), i));
        for (int i = 0; i < imageIds.size(); i++) {
            byId.get(imageIds.get(i)).changeSortOrder(i);
        }
        return list(spaceId);
    }

    private List<ImageResponse> list(Long spaceId) {
        return imageRepository.findBySpaceIdOrderBySortOrderAsc(spaceId).stream()
                .sorted(Comparator.comparing(SpaceImage::getSortOrder))
                .map(ImageResponse::from).toList();
    }

    private void check(MultipartFile file) {
        if (!ALLOWED.containsKey(file.getContentType())) {
            throw badRequest("jpg 또는 png 사진만 올릴 수 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw badRequest("사진은 장당 5MB 이하입니다.");
        }
    }

    private void store(MultipartFile file, String fileName) {
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw badRequest("잘못된 파일 경로입니다.");
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사진 저장에 실패했습니다.");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
