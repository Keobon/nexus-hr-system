package com.nexuslabs.hr.domain.leave.dto;

import jakarta.validation.constraints.Size;

/** POST /api/me/leave-requests/{id}/cancel-request 본문(F-LEAVE-06). 취소 사유는 승인함 details.cancelReason 으로 보인다. */
public record LeaveCancelRequest(@Size(max = 255) String reason) {
}
