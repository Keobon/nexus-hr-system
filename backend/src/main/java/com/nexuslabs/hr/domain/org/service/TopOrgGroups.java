package com.nexuslabs.hr.domain.org.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 조직을 "최상위 바로 아래 조직(하위 포함)"으로 묶은 결과 — 홈 성과 · 인건비 위젯과 퇴사 통계가 같이 쓴다.
 * 최상위에 바로 속한 사람은 최상위로 묶이고 맨 뒤에 온다. 지금 조직 트리에 없는 조직 ID 는 최상위로 본다.
 * 만들기는 {@link TopOrgGroupReader}.
 */
public record TopOrgGroups(long rootId, Map<Long, Long> group, Map<Long, String> names, Map<Long, Integer> order) {

    public long groupOf(long orgUnitId) {
        return group.getOrDefault(orgUnitId, rootId);
    }

    public String name(long groupId) {
        return names.get(groupId);
    }

    /** 조직 순서(sort_order, id)대로, 최상위는 맨 뒤. */
    public List<Long> ordered(Collection<Long> groupIds) {
        return groupIds.stream()
                .sorted(Comparator.comparing((Long g) -> g == rootId).thenComparing(order::get))
                .toList();
    }
}
