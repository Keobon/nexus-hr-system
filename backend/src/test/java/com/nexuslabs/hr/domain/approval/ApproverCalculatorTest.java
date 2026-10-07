package com.nexuslabs.hr.domain.approval;

import com.nexuslabs.hr.domain.approval.service.ApprovalPlan;
import com.nexuslabs.hr.domain.approval.service.ApprovalPlan.SkipReason;
import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 승인자 계산 — 백엔드 개발 안내 v2 6.3 "승인자" 표 5건 + 승인자 없음(기능명세서 9.1). */
@SpringBootTest
@Transactional
class ApproverCalculatorTest {

    @Autowired ApprovalService approvalService;
    @Autowired TestFixture fixture;
    @Autowired JdbcTemplate jdbc;

    DemoOrg org;

    @BeforeEach
    void setUp() {
        org = new DemoOrg(fixture, jdbc);
    }

    private ApprovalPlan plan(ApprovalWorkType type, TestFixture.Employee applicant) {
        return approvalService.preview(org.company.id(), type, applicant.id());
    }

    /** 단계마다 "승인자ID" 또는 "승인자ID:생략사유" */
    private static String[] describe(ApprovalPlan plan) {
        return plan.steps().stream()
                .map(s -> s.approverId() + (s.skipped() ? ":" + s.skipReason() : ""))
                .toArray(String[]::new);
    }

    @Test
    void 팀원_휴가는_팀장_그다음_본부장() {
        ApprovalPlan p = plan(ApprovalWorkType.LEAVE, org.cho);
        assertThat(p.approvalLineName()).isEqualTo("휴가 기본");
        assertThat(describe(p)).containsExactly(org.seo.id() + "", org.kang.id() + "");
        assertThat(p.immediatelyApproved()).isFalse();
    }

    @Test
    void 팀장_휴가는_본부장_한_번만_연속_같은_사람은_생략() {
        ApprovalPlan p = plan(ApprovalWorkType.LEAVE, org.seo);
        assertThat(describe(p)).containsExactly(org.kang.id() + "", org.kang.id() + ":" + SkipReason.SAME_AS_PREVIOUS);
    }

    @Test
    void 본부장_휴가는_조건이_맞는_승인선이_우선() {
        ApprovalPlan p = plan(ApprovalWorkType.LEAVE, org.kang);
        assertThat(p.approvalLineName()).isEqualTo("본부장 휴가");
        assertThat(describe(p)).containsExactly(org.ceo.id() + "");
    }

    @Test
    void 최상위_조직장은_모든_단계가_생략돼_즉시_승인() {
        ApprovalPlan p = plan(ApprovalWorkType.LEAVE, org.ceo);
        assertThat(p.steps()).allMatch(s -> s.skipReason() == SkipReason.TOP_OF_ORG);
        assertThat(p.immediatelyApproved()).isTrue();
    }

    @Test
    void 팀장_출장은_소속_조직장이_본인이라_상위_조직장() {
        ApprovalPlan p = plan(ApprovalWorkType.BUSINESS_TRIP, org.yoon);
        assertThat(describe(p)).containsExactly(org.kang.id() + "");
    }

    @Test
    void 조직장이_후보가_아니면_위로_올라간다() {
        jdbc.update("UPDATE account SET is_active = FALSE WHERE employee_id = ?", org.seo.id());
        assertThat(describe(plan(ApprovalWorkType.LEAVE, org.cho)))
                .containsExactly(org.kang.id() + "", org.kang.id() + ":" + SkipReason.SAME_AS_PREVIOUS);

        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", org.kang.id());
        assertThat(describe(plan(ApprovalWorkType.BUSINESS_TRIP, org.yoon))).containsExactly(org.ceo.id() + "");
    }

    @Test
    void 직책_보유자가_정확히_1명이_아니면_승인자_없음() {
        TestFixture.Employee second = fixture.employee(org.company.id(), org.company.rootOrgUnitId(), "직원", false);
        jdbc.update("UPDATE employee SET job_title_id = ? WHERE id = ?", org.ceoTitle, second.id());
        assertThatThrownBy(() -> plan(ApprovalWorkType.LEAVE, org.kang))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.APPROVER_NOT_FOUND);

        jdbc.update("UPDATE employee SET job_title_id = NULL WHERE job_title_id = ?", org.ceoTitle);
        assertThatThrownBy(() -> plan(ApprovalWorkType.LEAVE, org.kang))
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.APPROVER_NOT_FOUND);
    }

    @Test
    void 위로_올라가도_못_찾으면_최상위_조직장이_아닌_한_거부() {
        // 모든 조직장을 비운다 — 조현우는 최상위 조직장이 아니므로 거부
        jdbc.update("UPDATE org_unit SET lead_employee_id = NULL WHERE company_id = ?", org.company.id());
        assertThatThrownBy(() -> plan(ApprovalWorkType.LEAVE, org.cho))
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.APPROVER_NOT_FOUND);
    }

    @Test
    void 특정_직원_지정() {
        long line = jdbc.queryForObject(
                "SELECT id FROM approval_line WHERE company_id = ? AND work_type = 'TRIP_EXPENSE' AND is_default",
                Long.class, org.company.id());
        jdbc.update("UPDATE approval_line_step SET approver_type = 'EMPLOYEE', employee_id = ? WHERE approval_line_id = ?",
                org.company.adminId(), line);
        assertThat(describe(plan(ApprovalWorkType.TRIP_EXPENSE, org.cho)))
                .containsExactly(org.company.adminId() + "");
        // 본인이 지정된 직원이면 생략 → 즉시 승인
        ApprovalPlan self = approvalService.preview(org.company.id(), ApprovalWorkType.TRIP_EXPENSE, org.company.adminId());
        assertThat(Arrays.asList(describe(self))).containsExactly(org.company.adminId() + ":" + SkipReason.SELF);
        assertThat(self.immediatelyApproved()).isTrue();
    }
}
