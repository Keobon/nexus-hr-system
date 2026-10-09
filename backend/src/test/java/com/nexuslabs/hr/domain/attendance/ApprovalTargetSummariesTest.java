package com.nexuslabs.hr.domain.attendance;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import com.nexuslabs.hr.domain.attendance.service.BusinessTripApprovalTarget;
import com.nexuslabs.hr.domain.attendance.service.ExpenseClaimApprovalTarget;
import com.nexuslabs.hr.domain.attendance.service.OvertimeApprovalTarget;
import com.nexuslabs.hr.global.tenant.TenantContext;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestClock;
import com.nexuslabs.hr.support.TestFixture;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 승인 목록 요약 묶음 조회(역할 분담 2.1 "승인 업무 확정", ApprovalTarget.summaries) — 연장근무 · 출장 · 출장 경비.
 * 한 건씩 부른 summary 와 내용이 같고, 건수가 늘어도 쿼리는 한 번이고, 없는 ID는 맵에서 빠진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class ApprovalTargetSummariesTest {

    private static final long MISSING = 999_999_999L;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManagerFactory emf;
    @Autowired TestClock.MutableClock clock;
    @Autowired OvertimeApprovalTarget overtimeTarget;
    @Autowired BusinessTripApprovalTarget tripTarget;
    @Autowired ExpenseClaimApprovalTarget expenseTarget;

    DemoOrg org;
    long cid;

    @BeforeEach
    void setUp() {
        clock.set(java.time.LocalDateTime.of(2030, 3, 20, 9, 0));        // 출장 시작일이 지나야 경비를 청구할 수 있다
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
    }

    private long create(TestFixture.Employee e, String path, String body) throws Exception {
        String res = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", fixture.token(e.id(), cid)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private long trip(TestFixture.Employee e, String start, String end) throws Exception {
        return create(e, "/api/me/business-trips", """
                {"tripType": "DOMESTIC", "destination": "부산", "purpose": "고객사 미팅",
                 "startDate": "%s", "endDate": "%s"}""".formatted(start, end));
    }

    /** 묶음 조회 결과가 한 건씩 부른 결과와 같고, 쿼리 한 번으로 끝나는지 확인한다. */
    private void check(ApprovalTarget target, List<Long> ids) {
        TenantContext.set(cid);
        try {
            Map<Long, TargetSummary> single = tx.execute(s -> {
                Map<Long, TargetSummary> m = new LinkedHashMap<>();
                ids.forEach(id -> m.put(id, target.summary(id)));
                return m;
            });
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
            stats.setStatisticsEnabled(true);
            stats.clear();
            Map<Long, TargetSummary> bulk = tx.execute(s -> {
                List<Long> asked = new java.util.ArrayList<>(ids);
                asked.add(MISSING);
                return target.summaries(asked);
            });
            long queries = stats.getPrepareStatementCount();
            stats.setStatisticsEnabled(false);

            assertThat(bulk).isEqualTo(single);                    // 없는 ID는 빠진다
            assertThat(queries).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void 연장근무_요약을_쿼리_한_번으로_읽는다() throws Exception {
        String body = "{\"workDate\": \"%s\", \"plannedStart\": \"18:00\", \"plannedEnd\": \"20:00\", \"reason\": \"배포\"}";
        long a = create(org.cho, "/api/me/overtime-requests", body.formatted("2030-03-04"));
        long b = create(org.cho, "/api/me/overtime-requests", body.formatted("2030-03-05"));
        long c = create(org.yoon, "/api/me/overtime-requests", body.formatted("2030-03-04"));
        check(overtimeTarget, List.of(a, b, c));
    }

    @Test
    void 출장_요약을_쿼리_한_번으로_읽는다() throws Exception {
        long a = trip(org.cho, "2030-03-11", "2030-03-12");
        long b = trip(org.cho, "2030-03-18", "2030-03-18");
        long c = trip(org.yoon, "2030-03-11", "2030-03-11");
        check(tripTarget, List.of(a, b, c));
    }

    @Test
    void 출장_경비_요약은_출장과_경비_줄까지_쿼리_한_번으로_읽는다() throws Exception {
        long daily = jdbc.queryForObject("SELECT id FROM expense_type WHERE company_id = ? AND name = '일비'", Long.class, cid);
        List<Long> claims = new java.util.ArrayList<>();
        for (String day : List.of("2030-03-11", "2030-03-18")) {
            long tripId = trip(org.cho, day, day);
            long stepId = jdbc.queryForObject("""
                    SELECT id FROM approval_step WHERE company_id = ? AND work_type = 'BUSINESS_TRIP' AND target_id = ?
                    ORDER BY step_order LIMIT 1""", Long.class, cid, tripId);
            mvc.perform(post("/api/approvals/" + stepId + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content("{}").header("Authorization", fixture.token(org.seo.id(), cid))).andExpect(status().isOk());
            claims.add(create(org.cho, "/api/me/expense-claims", """
                    {"businessTripId": %d, "lines": [
                      {"expenseTypeId": %d, "usedDate": "%s", "amount": 30000, "description": "일비"},
                      {"expenseTypeId": %d, "usedDate": "%s", "amount": 20000, "description": "일비 2"}]}"""
                    .formatted(tripId, daily, day, daily, day)));
        }
        check(expenseTarget, claims);
    }
}
