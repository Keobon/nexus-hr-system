package com.nexuslabs.hr.domain.company.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.company.entity.Holiday;
import com.nexuslabs.hr.domain.company.entity.HolidayType;

import java.time.LocalDate;

/** 휴일 한 건. 연도별 목록에서는 매년 반복 휴일의 holidayDate 가 조회한 해의 날짜로 바뀌어 내려간다. */
public record HolidayResponse(long id, LocalDate holidayDate, String name, HolidayType holidayType,
                              @JsonProperty("isRecurring") boolean recurring) {

    public static HolidayResponse from(Holiday h) {
        return on(h, h.getHolidayDate());
    }

    public static HolidayResponse on(Holiday h, LocalDate date) {
        return new HolidayResponse(h.getId(), date, h.getName(), h.getHolidayType(), h.isRecurring());
    }
}
