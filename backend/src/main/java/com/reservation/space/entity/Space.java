
package com.reservation.space.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spaces")
@Getter
@Setter
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Integer capacity;

    private String location;

    @Column(nullable = false)
    private Boolean active = true;

    // 시간당 요금(원). 예약 금액 = 요금 × 이용 시간
    @Column(name = "price_per_hour")
    private Integer pricePerHour;

    // 공간 소개
    @Column(length = 1000)
    private String description;

    // 낙관적 락을 위한 버전 관리
    @Version
    @Column(name = "version")
    private Long version;
}
