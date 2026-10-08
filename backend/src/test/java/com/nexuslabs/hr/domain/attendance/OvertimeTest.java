package com.nexuslabs.hr.domain.attendance;

import com.nexuslabs.hr.domain.attendance.service.OvertimeService;
import com.nexuslabs.hr.global.tenant.TenantContext;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-06 연장근무 신청 (API 설계서 7.2) — 실제 승인 엔진과 함께(가짜 승인 대상 없이).
 * 연장근무 기본 승인선은 1단계 "소속 조직장"이다. 조현우 → 서예린(프론트엔드팀장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OvertimeTest {

    private static final String DATE = "2030-03-04";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired OvertimeService overtimeService;
    @Autowired TransactionTemplate tx;

    DemoOrg org;
    long cid;

    @BeforeEach
    void setUp() {
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, cid)));
    }

    private ResultActions apply(TestFixture.Employee e, String date, String start, String end) throws Exception {
        return as(e.id(), post("/api/me/overtime-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"workDate": "%s", "plannedStart": "%s", "plannedEnd": "%s", "reason": "배포 대응"}
                """.formatted(date, start, end)));
    }

    private long applyOk(TestFixture.Employee e, String date, String start, String end) throws Exception {
        String body = apply(e, date, start, end).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long stepId(long overtimeId) {
        return jdbc.queryForObject("""
                        SELECT id FROM approval_step WHERE company_id = ? AND work_type = 'OVERTIME' AND target_id = ?
                        ORDER BY round DESC, step_order LIMIT 1
                        """,
                Long.class, cid, overtimeId);
    }

    private ResultActions decide(TestFixture.Employee approver, long overtimeId, String action, String body)
            throws Exception {
        return as(approver.id(), post("/api/approvals/" + stepId(overtimeId) + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String overtimeStatus(long id) {
        return jdbc.queryForObject("SELECT status::text FROM overtime_request WHERE id = ?", String.class, id);
    }

    @Test
    void 신청하면_승인대기이고_소속_조직장이_승인하면_인정_시간으로_확정된다() throws Exception {
        long id = applyOk(org.cho, DATE, "18:00", "21:00");
        as(org.cho.id(), get("/api/overtime-requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(org.cho.id()))
                .andExpect(jsonPath("$.data.orgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.workDate").value(DATE))
                .andExpect(jsonPath("$.data.plannedStart").value("2030-03-04T18:00:00+09:00"))
                .andExpect(jsonPath("$.data.plannedEnd").value("2030-03-04T21:00:00+09:00"))
                .andExpect(jsonPath("$.data.requestedMinutes").value(180))
                .andExpect(jsonPath("$.data.approvedMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.approvalSteps[0].approverId").value(org.seo.id()))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("PENDING"));

        // 승인함 요약 — API 설계서 9.2 의 OVERTIME details 키
        as(org.seo.id(), get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.content[0].workType").value("OVERTIME"))
                .andExpect(jsonPath("$.data.content[0].requestedMinutes").value(180))
                .andExpect(jsonPath("$.data.content[0].details.workDate").value(DATE))
                .andExpect(jsonPath("$.data.content[0].details.plannedStart").value("2030-03-04T18:00:00+09:00"))
                .andExpect(jsonPath("$.data.content[0].details.plannedEnd").value("2030-03-04T21:00:00+09:00"))
                .andExpect(jsonPath("$.data.content[0].details.reason").value("배포 대응"));

        decide(org.seo, id, "approve", "{\"approvedMinutes\": 120}").andExpect(status().isOk());
        as(org.cho.id(), get("/api/overtime-requests/" + id))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedMinutes").value(120))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("APPROVED"));
    }

    @Test
    void 종료가_시작보다_이르거나_같으면_다음_날로_본다() throws Exception {
        long overnight = applyOk(org.cho, DATE, "22:00", "01:30");
        as(org.cho.id(), get("/api/overtime-requests/" + overnight))
                .andExpect(jsonPath("$.data.plannedEnd").value("2030-03-05T01:30:00+09:00"))
                .andExpect(jsonPath("$.data.requestedMinutes").value(210));

        long fullDay = applyOk(org.cho, "2030-03-05", "09:00", "09:00");
        as(org.cho.id(), get("/api/overtime-requests/" + fullDay))
                .andExpect(jsonPath("$.data.requestedMinutes").value(1440));
    }

    @Test
    void 승인자가_모두_생략되면_신청_즉시_신청_시간으로_승인된다() throws Exception {
        // 대표이사는 최상위 조직장 — 위로 올라갈 조직장이 없어 생략
        apply(org.ceo, DATE, "18:00", "20:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedMinutes").value(120))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("SKIPPED"));
    }

    @Test
    void 반려하면_반려되고_같은_날_다시_신청할_수_있다() throws Exception {
        long id = applyOk(org.cho, DATE, "18:00", "21:00");
        apply(org.cho, DATE, "19:00", "20:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OVERTIME_DUPLICATE"));

        decide(org.seo, id, "reject", "{\"comment\": \"필요 없음\"}").andExpect(status().isOk());
        assertThat(overtimeStatus(id)).isEqualTo("REJECTED");
        applyOk(org.cho, DATE, "19:00", "20:00");
    }

    @Test
    void 승인대기_중에만_본인이_철회하고_남은_단계도_닫힌다() throws Exception {
        long id = applyOk(org.cho, DATE, "18:00", "21:00");

        as(org.seo.id(), post("/api/me/overtime-requests/" + id + "/withdraw"))
                .andExpect(status().isNotFound());
        as(org.cho.id(), post("/api/me/overtime-requests/" + id + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("CANCELLED"));
        as(org.seo.id(), get("/api/approvals/inbox")).andExpect(jsonPath("$.data.totalElements").value(0));
        as(org.cho.id(), post("/api/me/overtime-requests/" + id + "/withdraw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        // 철회한 날은 다시 신청할 수 있다
        applyOk(org.cho, DATE, "18:00", "21:00");
    }

    @Test
    void 정산된_월의_날짜는_신청할_수_없다() throws Exception {
        jdbc.update("""
                        INSERT INTO payroll_run (company_id, pay_month, pay_date, company_name_snap, ceo_name_snap,
                                                 business_reg_no_snap, company_address_snap, confirmed_by)
                        VALUES (?, '2030-02', DATE '2030-03-10', '데모랩스', '대표', '000-00-00000', '서울', ?)
                        """,
                cid, org.company.adminId());

        apply(org.cho, "2030-02-27", "18:00", "21:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAY_MONTH_SETTLED"));
        applyOk(org.cho, DATE, "18:00", "21:00");
    }

    @Test
    void 그날_근태가_휴가_출장이면_거부하고_기록이_없거나_출근한_날은_된다() throws Exception {
        long tripId = jdbc.queryForObject("""
                        INSERT INTO business_trip (company_id, employee_id, trip_type, destination, purpose, start_date, end_date, status)
                        VALUES (?, ?, 'DOMESTIC', '부산', '고객사 방문', ?::date, ?::date, 'APPROVED') RETURNING id
                        """,
                Long.class, cid, org.cho.id(), DATE, DATE);
        jdbc.update("""
                        INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_method, business_trip_id)
                        VALUES (?, ?, ?::date, 'ON_BUSINESS_TRIP', 'BUSINESS_TRIP', 'SYSTEM', ?)
                        """,
                cid, org.cho.id(), DATE, tripId);

        apply(org.cho, DATE, "18:00", "21:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATT_ON_LEAVE_OR_TRIP"));
        applyOk(org.cho, "2030-03-05", "18:00", "21:00");
    }

    @Test
    void 입력_형식이_틀리면_필드별로_알려준다() throws Exception {
        as(org.cho.id(), post("/api/me/overtime-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"workDate": "2030-03-04", "plannedStart": "6pm", "plannedEnd": "24:00", "reason": " "}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.plannedStart").exists())
                .andExpect(jsonPath("$.error.fields.plannedEnd").exists())
                .andExpect(jsonPath("$.error.fields.reason").exists());
    }

    @Test
    void 휴직_중이면_신청할_수_없다() throws Exception {
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", org.cho.id());
        apply(org.cho, DATE, "18:00", "21:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_ACTIVE"));
    }

    @Test
    void 상세는_신청자_승인자_근태_조회_범위_안만_본다() throws Exception {
        long id = applyOk(org.cho, DATE, "18:00", "21:00");
        String path = "/api/overtime-requests/" + id;

        as(org.seo.id(), get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalSteps[0].isMyTurn").value(true));
        as(org.kang.id(), get(path)).andExpect(status().isOk());                    // 상위 조직장(팀 범위 안)
        as(org.company.adminId(), get(path)).andExpect(status().isOk());            // 전사
        as(org.yoon.id(), get(path))                                                 // 다른 팀 조직장
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));

        TestFixture.Company other = fixture.company("연장근무타사");
        mvc.perform(get(path).header("Authorization", fixture.token(other.adminId(), other.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void 퇴직_처리는_승인대기_신청만_취소하고_대기_건수는_승인대기만_센다() throws Exception {
        long pending = applyOk(org.cho, DATE, "18:00", "21:00");
        long approved = applyOk(org.cho, "2030-03-05", "18:00", "21:00");
        decide(org.seo, approved, "approve", "{}").andExpect(status().isOk());

        TenantContext.set(cid);
        try {
            assertThat(overtimeService.countPending(cid, org.cho.id())).isEqualTo(1);
            int cancelled = tx.execute(s -> overtimeService.cancelPendingByResignation(cid, org.cho.id()));
            assertThat(cancelled).isEqualTo(1);
            assertThat(overtimeService.countPending(cid, org.cho.id())).isZero();
        } finally {
            TenantContext.clear();
        }

        assertThat(overtimeStatus(pending)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM overtime_request WHERE id = ?", String.class, pending))
                .isEqualTo("퇴직");
        assertThat(overtimeStatus(approved)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForList("""
                        SELECT status::text FROM approval_step WHERE company_id = ? AND work_type = 'OVERTIME' AND target_id = ?
                        """,
                String.class, cid, pending)).isEqualTo(List.of("CANCELLED"));
    }
    private String name(TestFixture.Employee e) {
        return jdbc.queryForObject("SELECT name FROM employee WHERE id = ?", String.class, e.id());
    }

    @Test
    void 내_목록은_최근_근무일부터_현재_승인_단계와_함께_나온다() throws Exception {
        long approved = applyOk(org.cho, "2030-03-04", "18:00", "20:00");
        decide(org.seo, approved, "approve", "{\"approvedMinutes\": 60}").andExpect(status().isOk());
        long pending = applyOk(org.cho, "2030-03-06", "18:00", "21:00");
        applyOk(org.cho, "2030-04-01", "18:00", "19:00");

        as(org.cho.id(), get("/api/me/overtime-requests?month=2030-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].id").value(contains((int) pending, (int) approved)))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].approvedMinutes").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].currentStep.stepOrder").value(1))
                .andExpect(jsonPath("$.data.content[0].currentStep.totalSteps").value(1))
                .andExpect(jsonPath("$.data.content[0].currentStep.approverName").value(name(org.seo)))
                .andExpect(jsonPath("$.data.content[0].currentStep.status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[1].approvedMinutes").value(60))
                .andExpect(jsonPath("$.data.content[1].currentStep.status").value("APPROVED"));
        as(org.cho.id(), get("/api/me/overtime-requests")).andExpect(jsonPath("$.data.totalElements").value(3));
        as(org.cho.id(), get("/api/me/overtime-requests?status=APPROVED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        as(org.seo.id(), get("/api/me/overtime-requests")).andExpect(jsonPath("$.data.totalElements").value(0));

        // 모든 단계가 생략된 즉시 승인은 currentStep 이 null(필드는 있다)
        applyOk(org.ceo, DATE, "18:00", "19:00");
        as(org.ceo.id(), get("/api/me/overtime-requests"))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[0].currentStep").value(nullValue()));
    }

    @Test
    void 전체_목록은_조회_범위_안만_나오고_조직_필터는_하위_조직을_포함한다() throws Exception {
        applyOk(org.cho, DATE, "18:00", "20:00");
        applyOk(org.seo, DATE, "18:00", "20:00");
        applyOk(org.yoon, DATE, "18:00", "20:00");
        String path = "/api/overtime-requests?month=2030-03";

        as(org.company.adminId(), get(path)).andExpect(jsonPath("$.data.totalElements").value(3));
        as(org.company.adminId(), get(path + "&orgUnitId=" + org.frontendTeam))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        as(org.company.adminId(), get(path + "&orgUnitId=" + org.devDivision))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        as(org.seo.id(), get(path)).andExpect(jsonPath("$.data.totalElements").value(2));     // 프론트엔드팀장
        as(org.yoon.id(), get(path + "&orgUnitId=" + org.frontendTeam))                    // 범위 밖 조직 필터
                .andExpect(jsonPath("$.data.totalElements").value(0));
        as(org.cho.id(), get(path)).andExpect(jsonPath("$.data.totalElements").value(0));     // 조직장 아님
        as(org.company.adminId(), get(path + "&status=WRONG"))
                .andExpect(status().isBadRequest());
    }
}
