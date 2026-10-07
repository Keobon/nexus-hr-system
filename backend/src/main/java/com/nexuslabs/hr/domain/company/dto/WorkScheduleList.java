package com.nexuslabs.hr.domain.company.dto;

import java.util.List;

/**
 * GET /api/work-schedules — current 는 오늘 적용 중인 근무시간, upcoming 은 아직 시작 안 한 것(가까운 순),
 * history 는 지난 것(최근 순).
 */
public record WorkScheduleList(WorkScheduleResponse current, List<WorkScheduleResponse> upcoming,
                               List<WorkScheduleResponse> history) {
}
