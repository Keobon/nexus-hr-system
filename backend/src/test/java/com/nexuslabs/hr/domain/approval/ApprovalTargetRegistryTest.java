package com.nexuslabs.hr.domain.approval;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.ApprovalTargetRegistry;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 같은 업무 종류의 구현이 둘이면 @Primary 를 고른다(실제 구현 + 테스트용 가짜가 함께 있는 경우). */
class ApprovalTargetRegistryTest {

    record Target(ApprovalWorkType type, String name) implements ApprovalTarget {
        public void onFinalApproved(long targetId, ApprovalStepView last) {}
        public void onRejected(long targetId) {}
        public TargetSummary summary(long targetId) { return null; }
    }

    @Configuration
    static class RealAndPrimaryFake {
        @Bean ApprovalTarget real() { return new Target(ApprovalWorkType.LEAVE, "real"); }
        @Bean @Primary ApprovalTarget fake() { return new Target(ApprovalWorkType.LEAVE, "fake"); }
        @Bean ApprovalTarget overtime() { return new Target(ApprovalWorkType.OVERTIME, "overtime"); }
        @Bean ApprovalTargetRegistry registry(org.springframework.beans.factory.config.ConfigurableListableBeanFactory f) {
            return new ApprovalTargetRegistry(f);
        }
    }

    @Configuration
    static class TwoWithoutPrimary {
        @Bean ApprovalTarget a() { return new Target(ApprovalWorkType.LEAVE, "a"); }
        @Bean ApprovalTarget b() { return new Target(ApprovalWorkType.LEAVE, "b"); }
        @Bean ApprovalTargetRegistry registry(org.springframework.beans.factory.config.ConfigurableListableBeanFactory f) {
            return new ApprovalTargetRegistry(f);
        }
    }

    @Test
    void 둘이면_Primary를_쓰고_하나면_그것을_쓴다() {
        try (var ctx = new AnnotationConfigApplicationContext(RealAndPrimaryFake.class)) {
            ApprovalTargetRegistry registry = ctx.getBean(ApprovalTargetRegistry.class);
            assertThat(((Target) registry.get(ApprovalWorkType.LEAVE)).name()).isEqualTo("fake");
            assertThat(((Target) registry.get(ApprovalWorkType.OVERTIME)).name()).isEqualTo("overtime");
        }
    }

    @Test
    void Primary_없이_둘이면_부팅_실패() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(TwoWithoutPrimary.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEAVE");
    }
}
