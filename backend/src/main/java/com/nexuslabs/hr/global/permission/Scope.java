package com.nexuslabs.hr.global.permission;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;

import java.util.Set;

/**
 * 조회 범위. 전사면 회사 전체, 팀이면 employeeIds 안의 직원만(조직장이 아니면 비어 있다).
 * 목록은 {@link #employeeIds()}로 좁히고, 단건은 {@link #assertContains(long)}로 확인한다.
 */
public record Scope(boolean all, Set<Long> employeeIds) {

    public static Scope company() {
        return new Scope(true, Set.of());
    }

    public static Scope team(Set<Long> employeeIds) {
        return new Scope(false, Set.copyOf(employeeIds));
    }

    public boolean contains(long employeeId) {
        return all || employeeIds.contains(employeeId);
    }

    public void assertContains(long employeeId) {
        if (!contains(employeeId)) {
            throw new BusinessException(ErrorCode.OUT_OF_SCOPE);
        }
    }
}
