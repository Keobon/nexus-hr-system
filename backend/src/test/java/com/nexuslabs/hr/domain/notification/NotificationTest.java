package com.nexuslabs.hr.domain.notification;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.notification.service.SseEmitterRegistryAccess;
import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestClock;
import com.nexuslabs.hr.support.TestFixture;
import com.nexuslabs.hr.global.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결재 알림(B1, 2026-11-16). 휴가 기본 승인선은 조현우 → 서예린(1단계) → 강하늘(2단계), 대표는 모든 단계가 생략돼 즉시 승인.
 * 시계는 2030-03-04(월) 09:00. JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestClock.class, SseEmitterRegistryAccess.class})
class NotificationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;
    @Autowired ApprovalService approvalService;
    @Autowired TransactionTemplate tx;
    @Autowired SseEmitterRegistryAccess sse;

    DemoOrg org;
    long cid;
    long annual;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        annual = jdbc.queryForObject("SELECT id FROM leave_type WHERE company_id = ? AND name = '연차'", Long.class, cid);
        for (TestFixture.Employee e : new TestFixture.Employee[]{org.cho, org.ceo}) {
            jdbc.update("""
                    INSERT INTO leave_grant (company_id, employee_id, leave_type_id, leave_year, grant_type, days)
                    VALUES (?, ?, ?, 2030, 'REGULAR', 10)""", cid, e.id(), annual);
        }
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), cid)));
    }

    private long applyLeave(TestFixture.Employee e, String date) throws Exception {
        String body = as(e, post("/api/me/leave-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"leaveTypeId": %d, "startDate": "%s", "endDate": "%s", "reason": "가족 행사"}"""
                .formatted(annual, date, date)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long pendingStep(long leaveId) {
        return jdbc.queryForObject("""
                SELECT id FROM approval_step WHERE company_id = ? AND work_type = 'LEAVE' AND target_id = ?
                  AND status = 'PENDING'""", Long.class, cid, leaveId);
    }

    private int count(TestFixture.Employee e) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE company_id = ? AND recipient_id = ?",
                Integer.class, cid, e.id());
    }

    private String name(TestFixture.Employee e) {
        return jdbc.queryForObject("SELECT name FROM employee WHERE id = ?", String.class, e.id());
    }

    @Test
    void 휴가를_신청하면_1단계_승인자에게만_알림() throws Exception {
        long leave = applyLeave(org.cho, "2030-03-12");
        long step1 = pendingStep(leave);

        assertThat(count(org.seo)).isEqualTo(1);
        assertThat(count(org.cho)).isZero();
        assertThat(count(org.kang)).isZero();
        as(org.seo, get("/api/me/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("APPROVAL_REQUESTED"))
                .andExpect(jsonPath("$.data.content[0].workType").value("LEAVE"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(leave))
                .andExpect(jsonPath("$.data.content[0].approvalStepId").value(step1))
                .andExpect(jsonPath("$.data.content[0].title").value("휴가 결재가 올라왔습니다"))
                .andExpect(jsonPath("$.data.content[0].body").value(name(org.cho) + " · 연차 · 3/12(화) 1일"))
                .andExpect(jsonPath("$.data.content[0].linkPath").value("/approvals?stepId=" + step1))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
    }

    @Test
    void 승인으로_다음_단계가_차례가_되면_2단계_승인자에게_알림() throws Exception {
        long leave = applyLeave(org.cho, "2030-03-12");
        long step1 = pendingStep(leave);
        as(org.seo, post("/api/approvals/" + step1 + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        long step2 = pendingStep(leave);

        assertThat(step2).isNotEqualTo(step1);
        assertThat(count(org.kang)).isEqualTo(1);
        assertThat(count(org.seo)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT link_path FROM notification WHERE recipient_id = ?", String.class,
                org.kang.id())).isEqualTo("/approvals?stepId=" + step2);
        // 마지막 단계 승인 → 다음 차례가 없으니 알림도 없다
        as(org.kang, post("/api/approvals/" + step2 + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE company_id = ?", Integer.class, cid))
                .isEqualTo(2);
    }

    @Test
    void 모든_단계가_생략돼_즉시_승인되면_알림이_없다() throws Exception {
        long leave = applyLeave(org.ceo, "2030-03-12");
        assertThat(jdbc.queryForObject("SELECT status::text FROM leave_request WHERE id = ?", String.class, leave))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE company_id = ?", Integer.class, cid))
                .isZero();
    }

    @Test
    void 목록_안읽음_읽음처리_멱등_남의_알림() throws Exception {
        applyLeave(org.cho, "2030-03-12");
        applyLeave(org.cho, "2030-03-13");
        long first = jdbc.queryForObject("SELECT min(id) FROM notification WHERE recipient_id = ?", Long.class,
                org.seo.id());

        String readAt = JsonPath.read(as(org.seo, post("/api/me/notifications/" + first + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andReturn().getResponse().getContentAsString(), "$.data.readAt");
        clock.set(MONDAY.atTime(10, 0));
        as(org.seo, post("/api/me/notifications/" + first + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").value(readAt));

        // 안 읽은 것이 먼저
        as(org.seo, get("/api/me/notifications"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.content[1].id").value(first));
        as(org.seo, get("/api/me/notifications").param("unreadOnly", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].read").value(false));

        // 남의 알림은 404, 내 목록에도 안 나온다
        as(org.cho, post("/api/me/notifications/" + first + "/read")).andExpect(status().isNotFound());
        as(org.cho, get("/api/me/notifications")).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 결재_트랜잭션이_롤백되면_알림도_없다() {
        long targetId = 900_000_000L + cid;
        TenantContext.set(cid);
        try {
            tx.executeWithoutResult(s -> {
                approvalService.open(cid, ApprovalWorkType.LEAVE, targetId, org.cho.id());
                // 같은 트랜잭션 안에서는 저장돼 있다
                assertThat(count(org.seo)).isEqualTo(1);
                s.setRollbackOnly();
            });
        } finally {
            TenantContext.clear();
        }
        assertThat(count(org.seo)).isZero();
    }

    @Test
    void 알림_스트림은_연결하면_안읽은_개수를_보내고_커밋된_알림을_보낸다() throws Exception {
        applyLeave(org.cho, "2030-03-11");
        MvcResult stream = as(org.seo, get("/api/me/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        MockHttpServletResponse response = stream.getResponse();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("event:unread-count")
                .contains("\"unreadCount\":1");
        assertThat(sse.connections(cid, org.seo.id())).isEqualTo(1);

        applyLeave(org.cho, "2030-03-12");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("event:notification")
                .contains("휴가 결재가 올라왔습니다")
                .contains("3/12(화)")
                .doesNotContain("가족 행사");   // 사유 원문은 알림에 넣지 않는다

        sse.heartbeat();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains(":ping");
        sse.disconnect(cid, org.seo.id());
    }
}
