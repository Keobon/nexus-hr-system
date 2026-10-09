package com.nexuslabs.hr.domain.approval;

import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 승인 처리 — 신청 열기 · 승인함 · 승인 · 반려 · 철회 · 휴가 취소 · 재지정 (F-APPR-02·03, API 설계서 9.2). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FakeApprovalTargets.class)
class ApprovalFlowTest {

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

    /** 가짜 신청을 만들고 승인을 연다. 반환값은 targetId. */
    private long request(ApprovalWorkType type, TestFixture.Employee applicant, int requestedMinutes) {
        long targetId = 1000 + fakes.of(type).approved.size() + (long) (Math.random() * 1_000_000);
        fakes.of(type).request(targetId, applicant.id(), requestedMinutes);
        approvalService.open(cid, type, targetId, applicant.id());
        return targetId;
    }

    private long stepId(ApprovalWorkType type, long targetId, int stepOrder) {
        return jdbc.queryForObject("""
                SELECT id FROM approval_step WHERE company_id = ? AND work_type = ?::approval_work_type
                AND target_id = ? AND step_order = ? ORDER BY round DESC LIMIT 1""",
                Long.class, cid, type.name(), targetId, stepOrder);
    }

    private List<String> statuses(ApprovalWorkType type, long targetId) {
        return jdbc.queryForList("""
                SELECT status::text FROM approval_step WHERE company_id = ? AND work_type = ?::approval_work_type
                AND target_id = ? ORDER BY round, step_order""", String.class, cid, type.name(), targetId);
    }

    private ResultActions as(TestFixture.Employee e, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r)
            throws Exception {
        return mvc.perform(r.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions decide(TestFixture.Employee e, long stepId, String action, String body) throws Exception {
        return as(e, post("/api/approvals/" + stepId + "/" + action).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 두_단계를_차례로_승인하면_마지막에_업무가_확정된다() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        assertThat(statuses(ApprovalWorkType.LEAVE, leave)).containsExactly("PENDING", "WAITING");

        // 1단계 승인자의 승인함
        as(org.seo, get("/api/approvals/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].workType").value("LEAVE"))
                .andExpect(jsonPath("$.data.content[0].applicantId").value(org.cho.id()))
                .andExpect(jsonPath("$.data.content[0].applicantOrgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.content[0].stepOrder").value(1))
                .andExpect(jsonPath("$.data.content[0].totalSteps").value(2));
        as(org.kang, get("/api/approvals/inbox")).andExpect(jsonPath("$.data.totalElements").value(0));

        // 2단계 승인자는 아직 차례가 아니다
        decide(org.kang, stepId(ApprovalWorkType.LEAVE, leave, 2), "approve", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("APPROVAL_NOT_MY_TURN"));
        // 남의 단계
        decide(org.kang, stepId(ApprovalWorkType.LEAVE, leave, 1), "approve", "{}")
                .andExpect(jsonPath("$.error.code").value("APPROVAL_NOT_MY_TURN"));

        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "approve", "{\"comment\": \"확인했습니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.comment").value("확인했습니다"));
        assertThat(statuses(ApprovalWorkType.LEAVE, leave)).containsExactly("APPROVED", "PENDING");
        assertThat(fakes.of(ApprovalWorkType.LEAVE).approved).isEmpty();

        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "approve", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPROVAL_ALREADY_DONE"));

        // 2단계 승인자의 승인함에는 앞 단계 내역이 보인다
        as(org.kang, get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.content[0].stepOrder").value(2))
                .andExpect(jsonPath("$.data.content[0].previousSteps[0].approverId").value(org.seo.id()))
                .andExpect(jsonPath("$.data.content[0].previousSteps[0].status").value("APPROVED"));
        decide(org.kang, stepId(ApprovalWorkType.LEAVE, leave, 2), "approve", "{}").andExpect(status().isOk());

        assertThat(fakes.of(ApprovalWorkType.LEAVE).approved).containsExactly(leave);
        assertThat(fakes.of(ApprovalWorkType.LEAVE).lastSteps.getFirst().approverId()).isEqualTo(org.kang.id());
        as(org.seo, get("/api/approvals/history"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"));
    }

    @Test
    void 반려하면_남은_단계는_취소되고_신청이_반려된다() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "reject", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.comment").exists());
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "reject", "{\"comment\": \"일정 조정 필요\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(statuses(ApprovalWorkType.LEAVE, leave)).containsExactly("REJECTED", "CANCELLED");
        assertThat(fakes.of(ApprovalWorkType.LEAVE).rejected).containsExactly(leave);
        assertThat(fakes.of(ApprovalWorkType.LEAVE).approved).isEmpty();
    }

    @Test
    void 모든_단계가_생략되면_신청_즉시_확정() {
        long leave = request(ApprovalWorkType.LEAVE, org.ceo, 0);
        assertThat(statuses(ApprovalWorkType.LEAVE, leave)).containsExactly("SKIPPED", "SKIPPED");
        assertThat(fakes.of(ApprovalWorkType.LEAVE).approved).containsExactly(leave);
    }

    @Test
    void 생략된_단계는_건너뛰고_다음_단계가_차례() {
        long leave = request(ApprovalWorkType.LEAVE, org.seo, 0);
        assertThat(statuses(ApprovalWorkType.LEAVE, leave)).containsExactly("PENDING", "SKIPPED");
    }

    @Test
    void 연장근무는_단계마다_인정_시간을_줄일_수_있다() throws Exception {
        jdbc.update("""
                INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type, up_levels)
                SELECT company_id, id, 2, 'ORG_LEAD_UP', 1 FROM approval_line
                WHERE company_id = ? AND work_type = 'OVERTIME' AND is_default""", cid);
        long ot = request(ApprovalWorkType.OVERTIME, org.cho, 120);

        decide(org.seo, stepId(ApprovalWorkType.OVERTIME, ot, 1), "approve", "{\"approvedMinutes\": 150}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.maxMinutes").value(120));
        decide(org.seo, stepId(ApprovalWorkType.OVERTIME, ot, 1), "approve", "{\"approvedMinutes\": 90}")
                .andExpect(jsonPath("$.data.approvedMinutes").value(90));
        decide(org.kang, stepId(ApprovalWorkType.OVERTIME, ot, 2), "approve", "{\"approvedMinutes\": 100}")
                .andExpect(jsonPath("$.error.details.maxMinutes").value(90));
        // 비우면 앞 단계 값 그대로
        decide(org.kang, stepId(ApprovalWorkType.OVERTIME, ot, 2), "approve", "{}")
                .andExpect(jsonPath("$.data.approvedMinutes").value(90));
        assertThat(fakes.of(ApprovalWorkType.OVERTIME).lastSteps.getFirst().approvedMinutes()).isEqualTo(90);
    }

    @Test
    void 연장근무가_아니면_인정_시간을_받지_않는다() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "approve", "{\"approvedMinutes\": 60}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 철회하면_남은_단계가_취소되고_승인함에서_사라진다() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        approvalService.withdraw(cid, ApprovalWorkType.LEAVE, leave);
        assertThat(statuses(ApprovalWorkType.LEAVE, leave)).containsExactly("CANCELLED", "CANCELLED");
        as(org.seo, get("/api/approvals/inbox")).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 휴가_취소는_원래_휴가의_마지막_승인자가_승인하고_다시_요청하면_2회차() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "approve", "{}");
        decide(org.kang, stepId(ApprovalWorkType.LEAVE, leave, 2), "approve", "{}");

        fakes.of(ApprovalWorkType.LEAVE_CANCEL).request(leave, org.cho.id(), 0);
        assertThat(approvalService.openLeaveCancel(cid, leave, org.cho.id())).isFalse();
        long cancelStep = stepId(ApprovalWorkType.LEAVE_CANCEL, leave, 1);
        assertThat(jdbc.queryForObject("SELECT approver_id FROM approval_step WHERE id = ?", Long.class, cancelStep))
                .isEqualTo(org.kang.id());
        decide(org.kang, cancelStep, "reject", "{\"comment\": \"이미 대체 인력 배치\"}").andExpect(status().isOk());

        assertThat(approvalService.openLeaveCancel(cid, leave, org.cho.id())).isFalse();
        long second = stepId(ApprovalWorkType.LEAVE_CANCEL, leave, 1);
        assertThat(jdbc.queryForObject("SELECT round FROM approval_step WHERE id = ?", Integer.class, second)).isEqualTo(2);
        decide(org.kang, second, "approve", "{}").andExpect(status().isOk());
        assertThat(fakes.of(ApprovalWorkType.LEAVE_CANCEL).approved).containsExactly(leave);
    }

    @Test
    void 즉시_승인된_휴가의_취소도_즉시() {
        long leave = request(ApprovalWorkType.LEAVE, org.ceo, 0);
        fakes.of(ApprovalWorkType.LEAVE_CANCEL).request(leave, org.ceo.id(), 0);
        assertThat(approvalService.openLeaveCancel(cid, leave, org.ceo.id())).isTrue();
        assertThat(fakes.of(ApprovalWorkType.LEAVE_CANCEL).approved).containsExactly(leave);
    }

    @Test
    void 승인자_계정을_비활성화하면_재지정_목록에_나오고_관리자가_바꾼다() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        long step1 = stepId(ApprovalWorkType.LEAVE, leave, 1);
        TestFixture.Employee admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());

        as(admin, patch("/api/accounts/" + org.seo.id()).contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk());
        as(admin, get("/api/approvals/reassign-needed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].stepId").value(step1))
                .andExpect(jsonPath("$.data[0].approverId").value(org.seo.id()))
                .andExpect(jsonPath("$.data[0].applicantId").value(org.cho.id()));
        as(admin, get("/api/me")).andExpect(jsonPath("$.data.todos.reassignNeeded").value(1));
        // 재지정 목록은 APPROVAL_MANAGE 만
        as(org.cho, get("/api/approvals/reassign-needed")).andExpect(status().isForbidden());

        String path = "/api/approvals/" + step1 + "/approver";
        as(admin, patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"approverId\": " + org.cho.id() + "}"))
                .andExpect(status().isBadRequest()); // 신청자 본인
        as(admin, patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"approverId\": " + org.seo.id() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.approverId").exists()); // 비활성 계정
        TestFixture.Company other = fixture.company("다른회사");
        as(admin, patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"approverId\": " + other.adminId() + "}"))
                .andExpect(status().isNotFound()); // 다른 회사 직원
        as(admin, patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"approverId\": " + org.kang.id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approverId").value(org.kang.id()));

        as(admin, get("/api/approvals/reassign-needed")).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type = 'APPROVAL_STEP' AND target_id = ?",
                Long.class, step1)).isEqualTo(1);
        // 새 승인자가 바로 처리할 수 있다
        decide(org.kang, step1, "approve", "{}").andExpect(status().isOk());
    }

    @Test
    void 비활성_계정을_다시_활성화하면_재지정_필요_표시가_지워진다() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        TestFixture.Employee admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());
        String account = "/api/accounts/" + org.seo.id();

        as(admin, patch(account).contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk());
        as(admin, get("/api/approvals/reassign-needed")).andExpect(jsonPath("$.data.length()").value(1));
        as(admin, patch(account).contentType(MediaType.APPLICATION_JSON).content("{\"active\": true}"))
                .andExpect(status().isOk());
        as(admin, get("/api/approvals/reassign-needed")).andExpect(jsonPath("$.data.length()").value(0));
        // 원래 승인자가 그대로 처리한다
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, leave, 1), "approve", "{}").andExpect(status().isOk());
    }

    @Test
    void 목록이_여러_건이어도_행마다_신청자와_단계가_맞게_붙는다() throws Exception {
        long first = request(ApprovalWorkType.LEAVE, org.cho, 0);
        long second = request(ApprovalWorkType.LEAVE, org.cho, 0);
        long trip = request(ApprovalWorkType.BUSINESS_TRIP, org.yoon, 0); // 백엔드팀장 → 강하늘 1단계
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, first, 1), "approve", "{}").andExpect(status().isOk());
        decide(org.seo, stepId(ApprovalWorkType.LEAVE, second, 1), "approve", "{}").andExpect(status().isOk());

        // 강하늘의 승인함: 휴가 2건(2단계, 앞 단계 서예린) + 출장 1건(1단계, 앞 단계 없음)
        as(org.kang, get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[?(@.targetId == " + first + ")].applicantId").value(Math.toIntExact(org.cho.id())))
                .andExpect(jsonPath("$.data.content[?(@.targetId == " + first + ")].previousSteps[0].approverId")
                        .value(Math.toIntExact(org.seo.id())))
                .andExpect(jsonPath("$.data.content[?(@.targetId == " + second + ")].stepOrder").value(2))
                .andExpect(jsonPath("$.data.content[?(@.targetId == " + second + ")].previousSteps[0].approverId")
                        .value(Math.toIntExact(org.seo.id())))
                .andExpect(jsonPath("$.data.content[?(@.workType == 'BUSINESS_TRIP')].applicantId").value(Math.toIntExact(org.yoon.id())))
                .andExpect(jsonPath("$.data.content[?(@.workType == 'BUSINESS_TRIP')].previousSteps.length()").value(0));
        // 서예린의 처리 내역 2건
        as(org.seo, get("/api/approvals/history"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].status").value(contains("APPROVED", "APPROVED")))
                .andExpect(jsonPath("$.data.content[*].applicantId").value(contains(Math.toIntExact(org.cho.id()), Math.toIntExact(org.cho.id()))));
        // 강하늘 비활성 → 재지정 목록 3건, 행마다 승인자 이름
        TestFixture.Employee admin = new TestFixture.Employee(org.company.adminId(), cid, org.company.adminEmail());
        as(admin, patch("/api/accounts/" + org.kang.id()).contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk());
        as(admin, get("/api/approvals/reassign-needed"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[*].approverId").value(contains(Math.toIntExact(org.kang.id()), Math.toIntExact(org.kang.id()), Math.toIntExact(org.kang.id()))))
                .andExpect(jsonPath("$.data[?(@.targetId == " + trip + ")].applicantId").value(Math.toIntExact(org.yoon.id())));
    }

    @Test
    void 다른_회사의_단계는_404() throws Exception {
        long leave = request(ApprovalWorkType.LEAVE, org.cho, 0);
        TestFixture.Company other = fixture.company("다른회사");
        mvc.perform(post("/api/approvals/" + stepId(ApprovalWorkType.LEAVE, leave, 1) + "/approve")
                        .header("Authorization", fixture.token(other.adminId(), other.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
