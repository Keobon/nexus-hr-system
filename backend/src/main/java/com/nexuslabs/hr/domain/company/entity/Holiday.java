package com.nexuslabs.hr.domain.company.entity;

import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** 휴일. 매년 반복이면 처음 등록한 해의 날짜를 넣고 월·일로 비교한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate holidayDate;
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "holiday_type")
    private HolidayType holidayType;

    @Column(name = "is_recurring")
    private boolean recurring;

    public Holiday(LocalDate holidayDate, String name, HolidayType holidayType, boolean recurring) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.holidayType = holidayType;
        this.recurring = recurring;
    }

    public void update(LocalDate holidayDate, String name, HolidayType holidayType, boolean recurring) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.holidayType = holidayType;
        this.recurring = recurring;
    }
}
