package com.nexuslabs.hr.domain.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/me/expense-claims 본문(F-ATT-08, API 설계서 7.3 예시). 영수증은 먼저 POST /files(purpose RECEIPT)로 올린
 * 본인 파일의 ID(역할 분담 v2 2.3 27번). 줄 하나에 파일 하나, 같은 파일을 두 줄에 붙일 수 없다.
 */
public record ExpenseClaimCreateRequest(
        @NotNull Long businessTripId,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotNull Long expenseTypeId,
            @NotNull LocalDate usedDate,
            @NotNull @Positive Long amount,
            @Size(max = 255) String description,
            Long receiptFileId) {
    }
}
