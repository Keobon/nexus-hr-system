package com.nexuslabs.hr.domain.approval;

import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalStepStatus;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.CurrentStep;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.FakeApprovalTargets;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 목록 행의 currentStep(API 설계서 8장 "휴가 신청 목록 행") — 경계 경우마다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeApprovalTargets.class)
class CurrentStepsTest {

    private static final AtomicLong IDS = new AtomicLong(500_000);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired ApprovalService approvalService;
    @Autowired FakeApprovalTargets.Registry fakes;

    DemoOrg org;
    long cid;

    @BeforeEach
    void setUp() {
        fakes.reset();
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
    }

    /** 휴가 기본 승인선 = [1 소속 조직장, 2 한 단계 위 조직장]. 조현우 → 서예린 → 강하늘. */
    private long request(ApprovalWorkType type, TestFixture.Employee applicant) {
        long targetId = IDS.incrementAndGet();
        fakes.of(type).request(targetId, applicant.id(), 0);
        approvalService.open(cid, type, targetId, applicant.id());
        return targetId;
    }

    private void decide(TestFixture.Employee approver, ApprovalWorkType type, long targetId, String action, String body)
            throws Exception {
        long stepId = jdbc.queryForObject("""
                SELECT id FROM approval_step WHERE company_id = ? AND work_type = ?::approval_work_type AND target_id = ?
                AND status = 'PENDING'""", Long.class, cid, type.name(), targetId);
        mvc.perform(post("/api/approvals/" + stepId + "/" + action)
                        .header("Authorization", fixture.token(approver.id(), cid))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Map<Long, CurrentStep> current(long... ids) {
        return approvalService.currentSteps(cid, ApprovalWorkType.LEAVE, Arrays.stream(ids).boxed().toList());
    }

    @Test
    void 승인대기면_지금_차례_단계_끝났으면_마지막_처리_단계() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho);
        assertThat(current(leave).get(leave))
                .isEqualTo(new CurrentStep(1, 2, nameOf(org.seo), ApprovalStepStatus.PENDING));

        decide(org.seo, ApprovalWorkType.LEAVE, leave, "approve", "{}");
        assertThat(current(leave).get(leave))
                .isEqualTo(new CurrentStep(2, 2, nameOf(org.kang), ApprovalStepStatus.PENDING));

        decide(org.kang, ApprovalWorkType.LEAVE, leave, "approve", "{}");
        assertThat(current(leave).get(leave))
                .isEqualTo(new CurrentStep(2, 2, nameOf(org.kang), ApprovalStepStatus.APPROVED));
    }

    @Test
    void 반려되면_반려한_단계() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho);
        decide(org.seo, ApprovalWorkType.LEAVE, leave, "reject", "{\"comment\": \"일정 조정 필요\"}");
        assertThat(current(leave).get(leave))
                .isEqualTo(new CurrentStep(1, 2, nameOf(org.seo), ApprovalStepStatus.REJECTED));
    }

    @Test
    void 즉시_승인과_처리_전_철회는_없음_처리_후_철회는_마지막_처리_단계() throws Exception {
        long immediate = request(ApprovalWorkType.LEAVE, org.ceo); // 최상위 조직장 → 모든 단계 생략
        long withdrawnEarly = request(ApprovalWorkType.LEAVE, org.cho);
        approvalService.withdraw(cid, ApprovalWorkType.LEAVE, withdrawnEarly);
        long partly = request(ApprovalWorkType.LEAVE, org.cho);
        decide(org.seo, ApprovalWorkType.LEAVE, partly, "approve", "{}");
        approvalService.withdraw(cid, ApprovalWorkType.LEAVE, partly);

        Map<Long, CurrentStep> map = current(immediate, withdrawnEarly, partly);
        assertThat(map).doesNotContainKey(immediate).doesNotContainKey(withdrawnEarly);
        assertThat(map.get(partly)).isEqualTo(new CurrentStep(1, 2, nameOf(org.seo), ApprovalStepStatus.APPROVED));
    }

    @Test
    void 취소_요청_중이면_취소_승인_단계() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho);
        decide(org.seo, ApprovalWorkType.LEAVE, leave, "approve", "{}");
        decide(org.kang, ApprovalWorkType.LEAVE, leave, "approve", "{}");
        approvalService.openLeaveCancel(cid, leave, org.cho.id()); // 원래 마지막 승인자 강하늘에게 1단계

        assertThat(current(leave).get(leave))
                .isEqualTo(new CurrentStep(1, 1, nameOf(org.kang), ApprovalStepStatus.PENDING));
    }

    @Test
    void 다른_회사_신청이나_빈_목록은_없음() {
        long leave = request(ApprovalWorkType.LEAVE, org.cho);
        TestFixture.Company other = fixture.company("다른회사");
        assertThat(approvalService.currentSteps(other.id(), ApprovalWorkType.LEAVE, List.of(leave))).isEmpty();
        assertThat(approvalService.currentSteps(cid, ApprovalWorkType.LEAVE, List.of())).isEmpty();
        // 업무 종류가 다르면 안 섞인다
        assertThat(approvalService.currentSteps(cid, ApprovalWorkType.OVERTIME, List.of(leave))).isEmpty();
    }

    private String nameOf(TestFixture.Employee e) {
        return jdbc.queryForObject("SELECT name FROM employee WHERE id = ?", String.class, e.id());
    }
}
