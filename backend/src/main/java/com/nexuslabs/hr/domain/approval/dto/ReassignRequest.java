package com.nexuslabs.hr.domain.approval.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignRequest(@NotNull Long approverId) {
}
