package com.nexuslabs.hr.domain.leave;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.domain.leave.service.LeaveRequestService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-LEAVE-03 · 04 · 06 휴가 신청 · 조회 · 철회 · 취소 (API 설계서 8장) — 실제 승인 엔진 · 근태 반영과 함께.
 * 휴가 기본 승인선은 DemoOrg 의 [1 소속 조직장 → 2 한 단계 위 조직장] — 조현우 → 서예린 → 강하늘.
 * 시계는 2030-03-04(월) 09:00. 근무시간은 회사 등록 기본값(월–금), 휴가 연도는 1월 시작.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class LeaveRequestTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired TransactionTemplate tx;
    @Autowired DataSource dataSource;

    DemoOrg org;
    long cid;
    long annual;
    long sick;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        annual = typeId("연차");
        sick = typeId("병가");
        grant(org.cho, 10);
    }

    private long typeId(String name) {
        return jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = ?", Long.class, cid, name);
    }

    private void grant(TestFixture.Employee e, int days) {
        jdbc.update("""
                INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                VALUES (?, ?, ?, 2030, 'REGULAR', ?)""", cid, e.id(), annual, days);
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private ResultActions apply(TestFixture.Employee e, long typeId, String start, String end) throws Exception {
        return as(e, post("/api/me/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"leaveTypeId": %d, "startDate": "%s", "endDate": "%s", "reason": "가족 여행"}
                """.formatted(typeId, start, end)));
    }

    private long applyOk(TestFixture.Employee e, String start, String end) throws Exception {
        String body = apply(e, annual, start, end).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions decide(TestFixture.Employee approver, String workType, long leaveId, String action,
                                 String body) throws Exception {
        long stepId = jdbc.queryForObject("""
                        SELECT id FROM approval_step
                        WHERE company_id = ? AND work_type = ?::approval_work_type AND target_id = ? AND status = 'PENDING'
                        """,
                Long.class, cid, workType, leaveId);
        return as(approver, post("/api/approvals/" + stepId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long approvedLeave(String start, String end) throws Exception {
        long id = applyOk(org.cho, start, end);
        decide(org.seo, "LEAVE", id, "approve", "{}").andExpect(status().isOk());
        decide(org.kang, "LEAVE", id, "approve", "{}").andExpect(status().isOk());
        return id;
    }

    private List<String> leaveDays(long leaveId) {
        return jdbc.queryForList("""
                        SELECT work_date::text FROM attendance
                        WHERE company_id = ? AND leave_request_id = ? AND status = 'ON_VACATION' ORDER BY work_date
                        """,
                String.class, cid, leaveId);
    }

    /** 다른 트랜잭션이 커밋 전인 상황을 만들려고 별도 연결에서 SQL 을 실행한다. */
    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.execute();
        }
    }

    private CompletableFuture<MvcResult> async(TestFixture.Employee e, MockHttpServletRequestBuilder request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return as(e, request).andReturn();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** 요청이 행 잠금을 기다리기 시작할 때까지 기다린다. */
    private void awaitLockWait(Connection c) throws Exception {
        for (int i = 0; i < 250; i++) {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()
                    """); ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("요청이 잠금 대기에 들어가지 않았다");
    }

    private String leaveStatus(long id) {
        return jdbc.queryForObject("SELECT status::text FROM leave_request WHERE id = ?", String.class, id);
    }

    @Test
    void 미리보기는_신청과_같은_계산이고_저장하지_않는다() throws Exception {
        // 금–다음 주 화: 근무일 3일(주말 제외)
        as(org.cho, get("/api/me/leave-requests/preview?leaveTypeId=" + annual
                + "&startDate=2030-03-08&endDate=2030-03-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(3))
                .andExpect(jsonPath("$.data.leaveYear").value(2030))
                .andExpect(jsonPath("$.data.balance.leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.balance.granted").value(10))
                .andExpect(jsonPath("$.data.balance.remaining").value(10))
                .andExpect(jsonPath("$.data.balance.remainingAfter").value(7))
                .andExpect(jsonPath("$.data.approvalLine.name").value("휴가 기본"))
                .andExpect(jsonPath("$.data.steps[0].approverId").value(org.seo.id()))
                .andExpect(jsonPath("$.data.steps[1].approverId").value(org.kang.id()))
                .andExpect(jsonPath("$.data.steps[1].skipped").value(false))
                .andExpect(jsonPath("$.data.errors").value(empty()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request WHERE company_id = ?", Long.class, cid))
                .isZero();
    }

    @Test
    void 미리보기는_걸리는_오류를_모두_돌려준다() throws Exception {
        // 연말을 넘기고 잔여(부여 없음 = 0)보다 많다 — 두 오류가 함께 나온다
        as(org.yoon, get("/api/me/leave-requests/preview?leaveTypeId=" + annual
                + "&startDate=2030-12-30&endDate=2031-01-02"))
                .andExpect(jsonPath("$.data.errors").value(contains("LEAVE_CROSS_YEAR", "LEAVE_INSUFFICIENT_BALANCE")));
        as(org.cho, get("/api/me/leave-requests/preview?leaveTypeId=" + annual
                + "&startDate=2030-03-09&endDate=2030-03-10"))
                .andExpect(jsonPath("$.data.days").value(0))
                .andExpect(jsonPath("$.data.errors").value(contains("LEAVE_ZERO_DAYS")));
        // 차감 안 하는 종류는 잔여가 없다
        as(org.cho, get("/api/me/leave-requests/preview?leaveTypeId=" + sick
                + "&startDate=2030-03-05&endDate=2030-03-05"))
                .andExpect(jsonPath("$.data.balance").value(nullValue()))
                .andExpect(jsonPath("$.data.errors").value(empty()));
    }

    @Test
    void 신청하고_두_단계_승인되면_근무일마다_휴가_근태가_생기고_잔여가_준다() throws Exception {
        long id = applyOk(org.cho, "2030-03-08", "2030-03-12"); // 금 · 월 · 화 = 3일
        as(org.cho, get("/api/leave-requests/" + id))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.days").value(3))
                .andExpect(jsonPath("$.data.leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.reason").value("가족 여행"))
                .andExpect(jsonPath("$.data.approvalSteps[0].approverId").value(org.seo.id()))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("PENDING"));
        as(org.seo, get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.content[0].workType").value("LEAVE"))
                .andExpect(jsonPath("$.data.content[0].details.leaveTypeName").value("연차"))
                .andExpect(jsonPath("$.data.content[0].details.startDate").value("2030-03-08"))
                .andExpect(jsonPath("$.data.content[0].details.days").value(3))
                .andExpect(jsonPath("$.data.content[0].details.remaining").value(7));
        as(org.cho, get("/api/me/leave-balances?leaveYear=2030"))
                .andExpect(jsonPath("$.data.balances[0].pending").value(3));

        decide(org.seo, "LEAVE", id, "approve", "{}").andExpect(status().isOk());
        assertThat(leaveStatus(id)).isEqualTo("PENDING");
        decide(org.kang, "LEAVE", id, "approve", "{}").andExpect(status().isOk());
        assertThat(leaveStatus(id)).isEqualTo("APPROVED");
        assertThat(leaveDays(id)).containsExactly("2030-03-08", "2030-03-11", "2030-03-12");
        as(org.cho, get("/api/me/leave-balances?leaveYear=2030"))
                .andExpect(jsonPath("$.data.balances[0].used").value(3))
                .andExpect(jsonPath("$.data.balances[0].remaining").value(7));
    }

    @Test
    void 반려되면_반려이고_근태는_없다() throws Exception {
        long id = applyOk(org.cho, "2030-03-05", "2030-03-05");
        decide(org.seo, "LEAVE", id, "reject", "{\"comment\": \"일정 조정 필요\"}").andExpect(status().isOk());
        assertThat(leaveStatus(id)).isEqualTo("REJECTED");
        assertThat(leaveDays(id)).isEmpty();
    }

    @Test
    void 신청_거부_규칙() throws Exception {
        apply(org.cho, annual, "2030-03-06", "2030-03-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.endDate").exists());
        apply(org.cho, annual, "2030-03-09", "2030-03-10")
                .andExpect(jsonPath("$.error.code").value("LEAVE_ZERO_DAYS"));
        apply(org.cho, annual, "2030-12-31", "2031-01-02")
                .andExpect(jsonPath("$.error.code").value("LEAVE_CROSS_YEAR"));
        apply(org.cho, annual, "2030-03-04", "2030-03-15") // 10일 — 잔여 10이면 된다, 11일은 안 된다
                .andExpect(status().isCreated());
        apply(org.cho, annual, "2030-03-18", "2030-03-18")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAVE_INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.error.details.remaining").value(0))
                .andExpect(jsonPath("$.error.details.requested").value(1));
        // 차감 안 하는 종류는 잔여와 상관없이 되지만, 진행 중인 휴가와 겹치면 안 된다
        String body = apply(org.cho, sick, "2030-03-15", "2030-03-18")
                .andExpect(jsonPath("$.error.code").value("PERIOD_OVERLAP"))
                .andExpect(jsonPath("$.error.details.conflictType").value("LEAVE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.error.details.conflictId")).longValue()).isPositive();
        apply(org.cho, sick, "2030-03-18", "2030-03-18").andExpect(status().isCreated());
    }

    @Test
    void 진행_중인_출장과_겹치면_거부하고_출장_id를_알려준다() throws Exception {
        long tripId = jdbc.queryForObject("""
                INSERT INTO business_trip (company_id, employee_id, trip_type, destination, purpose, start_date, end_date)
                VALUES (?, ?, 'DOMESTIC', '부산', '고객사 방문', DATE '2030-03-07', DATE '2030-03-08') RETURNING id
                """, Long.class, cid, org.cho.id());
        apply(org.cho, annual, "2030-03-08", "2030-03-11")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PERIOD_OVERLAP"))
                .andExpect(jsonPath("$.error.details.conflictType").value("BUSINESS_TRIP"))
                .andExpect(jsonPath("$.error.details.conflictId").value(tripId));
        // 반려된 출장은 겹침이 아니다
        jdbc.update("UPDATE business_trip SET status = 'REJECTED' WHERE id = ?", tripId);
        apply(org.cho, annual, "2030-03-08", "2030-03-11").andExpect(status().isCreated());
    }

    @Test
    void 승인대기는_철회하고_남은_단계는_취소된다() throws Exception {
        long id = applyOk(org.cho, "2030-03-05", "2030-03-06");
        as(org.cho, post("/api/me/leave-requests/" + id + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("CANCELLED"));
        as(org.cho, post("/api/me/leave-requests/" + id + "/withdraw"))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        as(org.seo, post("/api/me/leave-requests/" + id + "/withdraw"))   // 남의 휴가
                .andExpect(status().isNotFound());
        // 철회한 기간은 다시 신청할 수 있다
        applyOk(org.cho, "2030-03-05", "2030-03-06");
    }

    @Test
    void 승인된_휴가의_취소_요청은_마지막_승인자가_승인하면_취소되고_근태가_지워진다() throws Exception {
        long id = approvedLeave("2030-03-11", "2030-03-12");
        as(org.cho, post("/api/me/leave-requests/" + id + "/cancel-request")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"일정 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCEL_REQUESTED"))
                .andExpect(jsonPath("$.data.cancelReason").value("일정 변경"))
                .andExpect(jsonPath("$.data.approvalSteps", hasSize(3)))
                .andExpect(jsonPath("$.data.approvalSteps[2].round").value(2))
                .andExpect(jsonPath("$.data.approvalSteps[2].approverId").value(org.kang.id()))
                .andExpect(jsonPath("$.data.approvalSteps[2].status").value("PENDING"));
        // 취소 요청 중에도 사용으로 친다
        as(org.cho, get("/api/me/leave-balances?leaveYear=2030"))
                .andExpect(jsonPath("$.data.balances[0].used").value(2));
        as(org.kang, get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.content[0].workType").value("LEAVE_CANCEL"))
                .andExpect(jsonPath("$.data.content[0].details.cancelReason").value("일정 변경"))
                .andExpect(jsonPath("$.data.content[0].details.days").value(2));
        as(org.cho, get("/api/me/leave-requests"))
                .andExpect(jsonPath("$.data.content[0].currentStep.approverName").exists())
                .andExpect(jsonPath("$.data.content[0].currentStep.totalSteps").value(1))
                .andExpect(jsonPath("$.data.content[0].currentStep.status").value("PENDING"));

        decide(org.kang, "LEAVE_CANCEL", id, "approve", "{}").andExpect(status().isOk());
        assertThat(leaveStatus(id)).isEqualTo("CANCELLED");
        assertThat(leaveDays(id)).isEmpty();
        as(org.cho, get("/api/me/leave-balances?leaveYear=2030"))
                .andExpect(jsonPath("$.data.balances[0].used").value(0));
    }

    @Test
    void 취소_요청이_반려되면_다시_승인완료이고_근태는_그대로() throws Exception {
        long id = approvedLeave("2030-03-11", "2030-03-11");
        // 사유는 선택이라 본문 없이 보내도 된다
        as(org.cho, post("/api/me/leave-requests/" + id + "/cancel-request")).andExpect(status().isOk());
        decide(org.kang, "LEAVE_CANCEL", id, "reject", "{\"comment\": \"대체 인력 없음\"}").andExpect(status().isOk());
        assertThat(leaveStatus(id)).isEqualTo("APPROVED");
        assertThat(leaveDays(id)).containsExactly("2030-03-11");
    }

    @Test
    void 철회와_최종_승인이_겹치면_승인이_먼저면_철회는_거부되고_승인이_유지된다() throws Exception {
        long id = applyOk(org.cho, "2030-03-11", "2030-03-11");
        decide(org.seo, "LEAVE", id, "approve", "{}").andExpect(status().isOk());
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            // 강하늘의 최종 승인이 처리 중(단계 · 휴가 행을 바꾸고 아직 커밋 전)
            exec(c, """
                    UPDATE approval_step SET status = 'APPROVED', acted_at = now()
                    WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ? AND status = 'PENDING'""", cid, id);
            exec(c, "UPDATE leave_request SET status = 'APPROVED' WHERE id = ?", id);
            CompletableFuture<MvcResult> withdraw = async(org.cho, post("/api/me/leave-requests/" + id + "/withdraw"));
            awaitLockWait(c);
            c.commit();
            MvcResult result = withdraw.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(result.getResponse().getContentAsString()).contains("INVALID_STATE");
        }
        assertThat(leaveStatus(id)).isEqualTo("APPROVED");
    }

    @Test
    void 취소_요청이_동시에_두_번_오면_한_건만_생긴다() throws Exception {
        long id = approvedLeave("2030-03-11", "2030-03-11");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            // 첫 번째 취소 요청이 처리 중(휴가 행을 바꾸고 취소 단계를 넣었고 아직 커밋 전)
            exec(c, "UPDATE leave_request SET status = 'CANCEL_REQUESTED' WHERE id = ?", id);
            exec(c, """
                    INSERT INTO approval_step (company_id, work_type, target_id, round, step_order, approver_id, status)
                    VALUES (?, 'LEAVE_CANCEL', ?, 1, 1, ?, 'PENDING')""", cid, id, org.kang.id());
            CompletableFuture<MvcResult> second = async(org.cho, post("/api/me/leave-requests/" + id + "/cancel-request"));
            awaitLockWait(c);
            c.commit();
            MvcResult result = second.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(result.getResponse().getContentAsString()).contains("INVALID_STATE");
        }
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM approval_step WHERE company_id = ? AND work_type = 'LEAVE_CANCEL' AND target_id = ?
                        """, Long.class, cid, id)).isEqualTo(1);
    }

    @Test
    void JSON_이_아닌_본문은_400() throws Exception {
        as(org.cho, post("/api/me/leave-requests").contentType(MediaType.TEXT_PLAIN).content("휴가"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 시작일_당일부터는_취소_요청할_수_없고_승인대기는_취소_요청_대상이_아니다() throws Exception {
        long approved = approvedLeave("2030-03-05", "2030-03-05");
        long pending = applyOk(org.cho, "2030-03-07", "2030-03-07");
        clock.set(MONDAY.plusDays(1).atTime(8, 0));
        as(org.cho, post("/api/me/leave-requests/" + approved + "/cancel-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.error.code").value("LEAVE_ALREADY_STARTED"));
        as(org.cho, post("/api/me/leave-requests/" + pending + "/cancel-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 최상위_조직장은_즉시_승인되고_취소도_즉시다() throws Exception {
        grant(org.ceo, 15);
        String body = apply(org.ceo, annual, "2030-03-05", "2030-03-06")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(leaveDays(id)).containsExactly("2030-03-05", "2030-03-06");
        as(org.ceo, get("/api/me/leave-requests"))
                .andExpect(jsonPath("$.data.content[0].currentStep").value(nullValue()));

        as(org.ceo, post("/api/me/leave-requests/" + id + "/cancel-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(leaveDays(id)).isEmpty();
    }

    @Test
    void 목록과_상세는_범위_안만_본다() throws Exception {
        long id = applyOk(org.cho, "2030-03-05", "2030-03-05");
        // 서예린(프론트엔드팀 조직장, LEAVE_READ 팀)은 조현우 것을 본다, 윤서연(백엔드팀)은 못 본다
        as(org.seo, get("/api/leave-requests"))
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].employeeName").exists())
                .andExpect(jsonPath("$.data.content[0].orgUnitName").value("프론트엔드팀"))
                .andExpect(jsonPath("$.data.content[0].leaveTypeId").value(annual))
                .andExpect(jsonPath("$.data.content[0].currentStep.stepOrder").value(1))
                .andExpect(jsonPath("$.data.content[0].currentStep.totalSteps").value(2));
        as(org.yoon, get("/api/leave-requests")).andExpect(jsonPath("$.data.content").value(empty()));
        as(org.yoon, get("/api/leave-requests/" + id)).andExpect(status().isForbidden());
        // 2단계 승인자 강하늘은 상세를 본다
        as(org.kang, get("/api/leave-requests/" + id)).andExpect(status().isOk());
        // 필터 — 종류 · 상태 · 기간
        as(org.seo, get("/api/leave-requests?leaveTypeId=" + sick)).andExpect(jsonPath("$.data.content").value(empty()));
        as(org.seo, get("/api/leave-requests?status=APPROVED")).andExpect(jsonPath("$.data.content").value(empty()));
        as(org.seo, get("/api/leave-requests?from=2030-03-06")).andExpect(jsonPath("$.data.content").value(empty()));
        as(org.cho, get("/api/me/leave-requests?leaveYear=2031")).andExpect(jsonPath("$.data.content").value(empty()));
    }

    @Test
    void 퇴직_자동_취소와_승인대기_건수() throws Exception {
        long pending = applyOk(org.cho, "2030-03-05", "2030-03-05");
        long approved = approvedLeave("2030-03-07", "2030-03-07");
        assertThat(leaveRequestService.countPending(cid, org.cho.id())).isEqualTo(1);

        int cancelled = tx.execute(s -> {
            TenantContext.set(cid);
            try {
                return leaveRequestService.cancelPendingByResignation(cid, org.cho.id());
            } finally {
                TenantContext.clear();
            }
        });
        assertThat(cancelled).isEqualTo(1);
        assertThat(leaveStatus(pending)).isEqualTo("CANCELLED");
        assertThat(leaveStatus(approved)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM leave_request WHERE id = ?", String.class, pending))
                .isEqualTo("퇴직");
        assertThat(jdbc.queryForList("""
                        SELECT status::text FROM approval_step WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ?
                        """, String.class, cid, pending)).containsOnly("CANCELLED");
        assertThat(leaveRequestService.countPending(cid, org.cho.id())).isZero();
    }
}
