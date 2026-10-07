package com.nexuslabs.hr.domain.company.dto;

import java.util.List;
import java.util.Map;

/**
 * GET /api/company/setup — 마법사 단계별 상태. counts 는 그 단계에서 관리하는 데이터의 개수,
 * done 은 그 단계에 데이터가 있다는 뜻이다. 모든 단계는 건너뛸 수 있어서 done 이 아니어도 설정을 완료할 수 있다.
 */
public record SetupStatusResponse(boolean setupCompleted, List<Step> steps) {

    public record Step(SetupStep step, Map<String, Integer> counts, boolean done) {
    }
}
