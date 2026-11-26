package com.reservation.space.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 공간의 요일별 운영 시간. 공간마다 요일당 한 줄 (space_id, day_of_week 유니크).
 */
@Entity
@Table(name = "space_operating_hours",
        uniqueConstraints = @UniqueConstraint(name = "uk_space_day", columnNames = {"space_id", "day_of_week"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceOperatingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    // 1=월 ... 7=일 (java.time.DayOfWeek.getValue() 와 같음)
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    // 휴무
    @Column(nullable = false)
    private Boolean closed;

    public static SpaceOperatingHour create(Space space, int dayOfWeek) {
        SpaceOperatingHour hour = new SpaceOperatingHour();
        hour.space = space;
        hour.dayOfWeek = dayOfWeek;
        return hour;
    }

    public void change(LocalTime openTime, LocalTime closeTime, boolean closed) {
        this.closed = closed;
        this.openTime = closed ? null : openTime;
        this.closeTime = closed ? null : closeTime;
    }

    public OperatingWindow toWindow() {
        return closed ? OperatingWindow.CLOSED : OperatingWindow.of(openTime, closeTime);
    }
}
