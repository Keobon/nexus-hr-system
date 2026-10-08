package com.nexuslabs.hr.domain.approval.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 업무 종류 → ApprovalTarget 구현체. 한 종류에 구현이 둘 이상이면 {@code @Primary} 가 붙은 하나를 쓰고,
 * 그런 게 없으면 부팅할 때 실패한다. 테스트용 가짜(FakeApprovalTargets)는 @Primary 라 실제 구현이 있어도 가짜가 쓰인다.
 *
 * <p>구현체가 ApprovalService 를 쓰는 경우(휴가 취소 등) 순환 참조가 되지 않도록, 모든 빈이 만들어진 뒤에 모은다.
 */
@Component
public class ApprovalTargetRegistry implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final Map<ApprovalWorkType, ApprovalTarget> targets = new EnumMap<>(ApprovalWorkType.class);

    public ApprovalTargetRegistry(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<ApprovalWorkType, List<String>> names = new EnumMap<>(ApprovalWorkType.class);
        for (String name : beanFactory.getBeanNamesForType(ApprovalTarget.class)) {
            ApprovalWorkType type = beanFactory.getBean(name, ApprovalTarget.class).type();
            names.computeIfAbsent(type, k -> new ArrayList<>()).add(name);
        }
        names.forEach((type, candidates) -> {
            List<String> chosen = candidates.size() == 1 ? candidates
                    : candidates.stream().filter(this::isPrimary).toList();
            if (chosen.size() != 1) {
                throw new IllegalStateException("ApprovalTarget 이 두 개 이상이고 @Primary 로 하나를 고를 수 없다: "
                        + type + " — " + candidates);
            }
            targets.put(type, beanFactory.getBean(chosen.getFirst(), ApprovalTarget.class));
        });
    }

    public ApprovalTarget get(ApprovalWorkType type) {
        ApprovalTarget target = targets.get(type);
        if (target == null) {
            // 아직 구현되지 않은 업무(B-08 · B-12 진행 중)
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "아직 구현되지 않은 승인 업무입니다: " + type);
        }
        return target;
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName) && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
