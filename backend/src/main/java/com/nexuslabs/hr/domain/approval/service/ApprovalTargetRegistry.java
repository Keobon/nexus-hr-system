package com.nexuslabs.hr.domain.approval.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** 업무 종류 → ApprovalTarget 구현체. 같은 종류를 두 곳에서 구현하면 부팅할 때 실패한다. */
@Component
public class ApprovalTargetRegistry {

    private final Map<ApprovalWorkType, ApprovalTarget> targets = new EnumMap<>(ApprovalWorkType.class);

    /** 구현체가 아직 하나도 없어도(B-08 · B-12 전) 부팅되도록 ObjectProvider 로 받는다. */
    public ApprovalTargetRegistry(ObjectProvider<ApprovalTarget> implementations) {
        for (ApprovalTarget t : implementations.orderedStream().toList()) {
            ApprovalTarget previous = targets.put(t.type(), t);
            if (previous != null) {
                throw new IllegalStateException("ApprovalTarget 이 두 개다: " + t.type() + " — "
                        + previous.getClass().getName() + ", " + t.getClass().getName());
            }
        }
    }

    public ApprovalTarget get(ApprovalWorkType type) {
        ApprovalTarget target = targets.get(type);
        if (target == null) {
            // 아직 구현되지 않은 업무(B-08 · B-12 진행 중)
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "아직 구현되지 않은 승인 업무입니다: " + type);
        }
        return target;
    }
}
