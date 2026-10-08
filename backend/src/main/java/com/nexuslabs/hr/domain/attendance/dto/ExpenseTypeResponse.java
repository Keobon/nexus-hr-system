package com.nexuslabs.hr.domain.attendance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseType;

/** 출장 경비 종류 한 건(API 설계서 7.3). */
public record ExpenseTypeResponse(long id, String name, boolean receiptRequired, int sortOrder,
                                  @JsonProperty("isActive") boolean active) {

    public static ExpenseTypeResponse from(ExpenseType type) {
        return new ExpenseTypeResponse(type.getId(), type.getName(), type.isReceiptRequired(), type.getSortOrder(),
                type.isActive());
    }
}
