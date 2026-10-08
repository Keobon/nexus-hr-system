package com.nexuslabs.hr.domain.attendance;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.domain.attendance.service.BusinessTripService;
import com.nexuslabs.hr.global.tenant.TenantContext;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestClock;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-07 출장 신청 · 결과 보고 (API 설계서 7.3) — 실제 승인 엔진과 함께. 출장 기본 승인선은 1단계 "소속 조직장".
 * 시계는 2030-03-04(월) 09:00 에서 시작한다. 근무시간은 회사 등록 기본값(월–금).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class BusinessTripTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;
    @Autowired BusinessTripService businessTripService;
    @Autowired TransactionTemplate tx;

    DemoOrg org;
    long cid;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, cid)));
    }

    private ResultActions apply(TestFixture.Employee e, String start, String end) throws Exception {
        return as(e.id(), post("/api/me/business-trips").contentType(MediaType.APPLICATION_JSON).content("""
                {"tripType": "DOMESTIC", "destination": "부산", "purpose": "고객사 방문",
                 "startDate": "%s", "endDate": "%s", "estimatedCost": 300000}
                """.formatted(start, end)));
    }

    private long applyOk(TestFixture.Employee e, String start, String end) throws Exception {
        String body = apply(e, start, end).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions decide(TestFixture.Employee approver, long tripId, String action, String body) throws Exception {
        long stepId = jdbc.queryForObject("""
                        SELECT id FROM approval_step WHERE company_id = ? AND work_type = 'BUSINESS_TRIP' AND target_id = ?
                        ORDER BY round DESC, step_order LIMIT 1
                        """,
                Long.class, cid, tripId);
        return as(approver.id(), post("/api/approvals/" + stepId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<String> tripDays(long tripId) {
        return jdbc.queryForList("""
                        SELECT work_date::text FROM attendance
                        WHERE company_id = ? AND business_trip_id = ? AND status = 'ON_BUSINESS_TRIP' ORDER BY work_date
                        """,
                String.class, cid, tripId);
    }

    private String tripStatus(long id) {
        return jdbc.queryForObject("SELECT status::text FROM business_trip WHERE id = ?", String.class, id);
    }

    @Test
    void 신청하면_승인대기이고_승인되면_기간의_근무일마다_출장_근태가_생긴다() throws Exception {
        // 금요일 출근 기록이 있는 날은 덮어쓰지 않는다(정정 대상의 충돌)
        jdbc.update("""
                        INSERT INTO attendance (company_id, employee_id, work_date, status, work_type, check_in_at, check_in_method)
                        VALUES (?, ?, DATE '2030-03-08', 'CHECKED_IN', 'OFFICE', TIMESTAMPTZ '2030-03-08 09:00+09', 'WEB')
                        """,
                cid, org.cho.id());
        long id = applyOk(org.cho, "2030-03-06", "2030-03-10");
        as(org.cho.id(), get("/api/business-trips/" + id))
                .andExpect(jsonPath("$.data.tripType").value("DOMESTIC"))
                .andExpect(jsonPath("$.data.destination").value("부산"))
                .andExpect(jsonPath("$.data.estimatedCost").value(300000))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reportText").value(nullValue()))
                .andExpect(jsonPath("$.data.expenseClaim").value(nullValue()))
                .andExpect(jsonPath("$.data.approvalSteps[0].approverId").value(org.seo.id()));
        as(org.seo.id(), get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.content[0].workType").value("BUSINESS_TRIP"))
                .andExpect(jsonPath("$.data.content[0].details.destination").value("부산"))
                .andExpect(jsonPath("$.data.content[0].details.startDate").value("2030-03-06"))
                .andExpect(jsonPath("$.data.content[0].details.tripType").value("DOMESTIC"));
        assertThat(tripDays(id)).isEmpty();

        decide(org.seo, id, "approve", "{}").andExpect(status().isOk());
        assertThat(tripStatus(id)).isEqualTo("APPROVED");
        assertThat(tripDays(id)).containsExactly("2030-03-06", "2030-03-07");      // 금 출근 · 토일 제외
    }

    @Test
    void 승인자가_모두_생략되면_즉시_승인되고_근태도_바로_생긴다() throws Exception {
        String body = apply(org.ceo, "2030-03-04", "2030-03-05")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(tripDays(id)).containsExactly("2030-03-04", "2030-03-05");
    }

    @Test
    void 반려하면_근태가_생기지_않고_같은_기간을_다시_신청할_수_있다() throws Exception {
        long id = applyOk(org.cho, "2030-03-04", "2030-03-05");
        decide(org.seo, id, "reject", "{\"comment\": \"일정 조정\"}").andExpect(status().isOk());
        assertThat(tripStatus(id)).isEqualTo("REJECTED");
        assertThat(tripDays(id)).isEmpty();
        applyOk(org.cho, "2030-03-04", "2030-03-05");
    }

    @Test
    void 내_휴가나_진행_중_출장과_날짜가_겹치면_거부한다() throws Exception {
        long trip = applyOk(org.cho, "2030-03-04", "2030-03-05");
        apply(org.cho, "2030-03-05", "2030-03-06")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PERIOD_OVERLAP"))
                .andExpect(jsonPath("$.error.details.conflictType").value("BUSINESS_TRIP"))
                .andExpect(jsonPath("$.error.details.conflictId").value(trip));

        long annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'",
                Long.class, cid);
        long leave = jdbc.queryForObject("""
                        INSERT INTO leave_request (company_id, employee_id, leave_type_id, leave_year, start_date, end_date, days, status)
                        VALUES (?, ?, ?, 2030, DATE '2030-03-11', DATE '2030-03-12', 2, 'CANCEL_REQUESTED') RETURNING id
                        """,
                Long.class, cid, org.cho.id(), annual);
        apply(org.cho, "2030-03-12", "2030-03-13")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details.conflictType").value("LEAVE"))
                .andExpect(jsonPath("$.error.details.conflictId").value(leave));

        // 철회한 출장 · 다른 직원은 겹침이 아니다
        as(org.cho.id(), post("/api/me/business-trips/" + trip + "/withdraw")).andExpect(status().isOk());
        applyOk(org.cho, "2030-03-05", "2030-03-06");
        applyOk(org.seo, "2030-03-11", "2030-03-12");
    }

    @Test
    void 입력이_틀리면_거부한다() throws Exception {
        apply(org.cho, "2030-03-05", "2030-03-04")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.endDate").exists());
        as(org.cho.id(), post("/api/me/business-trips").contentType(MediaType.APPLICATION_JSON).content("""
                {"tripType": "SPACE", "destination": "달", "purpose": "탐사", "startDate": "2030-03-04", "endDate": "2030-03-04"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        as(org.cho.id(), post("/api/me/business-trips").contentType(MediaType.APPLICATION_JSON).content("""
                {"tripType": "DOMESTIC", "destination": " ", "purpose": "방문", "startDate": "2030-03-04",
                 "endDate": "2030-03-04", "estimatedCost": -1}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.destination").exists())
                .andExpect(jsonPath("$.error.fields.estimatedCost").exists());
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", org.cho.id());
        apply(org.cho, "2030-03-04", "2030-03-04")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_NOT_ACTIVE"));
    }

    @Test
    void 승인대기_중에만_본인이_철회한다() throws Exception {
        long id = applyOk(org.cho, "2030-03-04", "2030-03-05");
        as(org.seo.id(), post("/api/me/business-trips/" + id + "/withdraw")).andExpect(status().isNotFound());
        as(org.cho.id(), post("/api/me/business-trips/" + id + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("CANCELLED"));

        long approved = applyOk(org.cho, "2030-03-11", "2030-03-11");
        decide(org.seo, approved, "approve", "{}").andExpect(status().isOk());
        as(org.cho.id(), post("/api/me/business-trips/" + approved + "/withdraw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 결과_보고는_승인된_출장의_종료일부터_본인이_쓰고_고친다() throws Exception {
        long id = applyOk(org.cho, "2030-03-04", "2030-03-05");
        String report = "{\"reportText\": \" 계약 조건 협의 완료 \"}";
        as(org.cho.id(), put("/api/me/business-trips/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));          // 승인대기
        decide(org.seo, id, "approve", "{}").andExpect(status().isOk());
        as(org.cho.id(), put("/api/me/business-trips/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));          // 종료일 전

        clock.set(MONDAY.plusDays(1).atTime(18, 0));
        as(org.seo.id(), put("/api/me/business-trips/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isNotFound());
        as(org.cho.id(), put("/api/me/business-trips/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportText").value("계약 조건 협의 완료"))
                .andExpect(jsonPath("$.data.reportedAt").value("2030-03-05T18:00:00+09:00"));
        as(org.cho.id(), put("/api/me/business-trips/" + id + "/report").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportText\": \"수정\"}"))
                .andExpect(jsonPath("$.data.reportText").value("수정"));
        as(org.cho.id(), put("/api/me/business-trips/" + id + "/report").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportText\": \"\"}"))
                .andExpect(status().isBadRequest());
        as(org.cho.id(), get("/api/me/business-trips"))
                .andExpect(jsonPath("$.data.content[0].reportWritten").value(true));
    }

    @Test
    void 상세는_신청자_승인자_근태_조회_범위_안만_본다() throws Exception {
        long id = applyOk(org.cho, "2030-03-04", "2030-03-05");
        String path = "/api/business-trips/" + id;
        as(org.seo.id(), get(path)).andExpect(jsonPath("$.data.approvalSteps[0].isMyTurn").value(true));
        as(org.kang.id(), get(path)).andExpect(status().isOk());
        as(org.company.adminId(), get(path)).andExpect(status().isOk());
        as(org.yoon.id(), get(path))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
        TestFixture.Company other = fixture.company("출장타사");
        mvc.perform(get(path).header("Authorization", fixture.token(other.adminId(), other.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void 목록은_최근_시작일부터_범위_안만_나오고_기간_조직_필터가_걸린다() throws Exception {
        long march = applyOk(org.cho, "2030-03-04", "2030-03-05");
        long april = applyOk(org.cho, "2030-04-01", "2030-04-02");
        applyOk(org.yoon, "2030-03-04", "2030-03-04");

        as(org.cho.id(), get("/api/me/business-trips"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].id").value(contains((int) april, (int) march)))
                .andExpect(jsonPath("$.data.content[0].reportWritten").value(false))
                .andExpect(jsonPath("$.data.content[0].expenseClaimStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].currentStep.status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].currentStep.totalSteps").value(1));

        String path = "/api/business-trips";
        as(org.company.adminId(), get(path)).andExpect(jsonPath("$.data.totalElements").value(3));
        as(org.company.adminId(), get(path + "?from=2030-03-05&to=2030-03-31"))
                .andExpect(jsonPath("$.data.totalElements").value(1));                   // 3/4–3/5 만 걸친다
        as(org.company.adminId(), get(path + "?orgUnitId=" + org.backendTeam))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        as(org.seo.id(), get(path)).andExpect(jsonPath("$.data.totalElements").value(2));
        as(org.cho.id(), get(path)).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 퇴직_처리는_승인대기_출장만_취소하고_대기_건수는_승인대기만_센다() throws Exception {
        long pending = applyOk(org.cho, "2030-03-04", "2030-03-05");
        long approved = applyOk(org.cho, "2030-03-11", "2030-03-11");
        decide(org.seo, approved, "approve", "{}").andExpect(status().isOk());

        TenantContext.set(cid);
        try {
            assertThat(businessTripService.countPending(cid, org.cho.id())).isEqualTo(1);
            int cancelled = tx.execute(s -> businessTripService.cancelPendingByResignation(cid, org.cho.id()));
            assertThat(cancelled).isEqualTo(1);
            assertThat(businessTripService.countPending(cid, org.cho.id())).isZero();
        } finally {
            TenantContext.clear();
        }
        assertThat(tripStatus(pending)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM business_trip WHERE id = ?", String.class, pending))
                .isEqualTo("퇴직");
        assertThat(tripStatus(approved)).isEqualTo("APPROVED");
    }
}
