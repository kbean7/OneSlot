package com.reservation.space.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "space_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    // 저장된 파일 이름 (업로드 폴더 기준). 화면에서는 /uploads/{filePath} 로 접근
    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "original_name", length = 255)
    private String originalName;

    // 0 이 대표 사진
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SpaceImage create(Space space, String filePath, String originalName, int sortOrder) {
        SpaceImage image = new SpaceImage();
        image.space = space;
        image.filePath = filePath;
        image.originalName = originalName;
        image.sortOrder = sortOrder;
        return image;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
