package com.nexuslabs.hr.domain.attendance;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.domain.attendance.service.ExpenseClaimService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-08 출장 경비 청구 (API 설계서 7.3) — 실제 승인 엔진과 함께. 경비 기본 승인선은 1단계 "소속 조직장".
 * 조현우의 출장(2030-03-04–05, 서예린 승인)에 청구한다. 시계는 2030-03-04(월) 09:00.
 * 회사 등록 기본 경비 종류: 교통비 · 숙박비 · 식비 · 기타(영수증 필수), 일비(불필요).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class ExpenseClaimTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;
    @Autowired ExpenseClaimService expenseClaimService;
    @Autowired TransactionTemplate tx;

    DemoOrg org;
    long cid;
    long trip;
    long transport;
    long perDiem;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.atTime(9, 0));
        org = new DemoOrg(fixture, jdbc);
        cid = org.company.id();
        trip = approvedTrip(org.cho, "2030-03-04", "2030-03-05");
        transport = typeId("교통비");
        perDiem = typeId("일비");
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, cid)));
    }

    private long typeId(String name) {
        return jdbc.queryForObject("SELECT id FROM expense_type WHERE company_id = ? AND name = ?", Long.class, cid, name);
    }

    private long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long approvedTrip(TestFixture.Employee e, String start, String end) throws Exception {
        long tripId = id(as(e.id(), post("/api/me/business-trips").contentType(MediaType.APPLICATION_JSON).content("""
                {"tripType": "DOMESTIC", "destination": "부산", "purpose": "고객사 방문", "startDate": "%s", "endDate": "%s"}
                """.formatted(start, end))).andExpect(status().isCreated()));
        approve(org.seo, "BUSINESS_TRIP", tripId);
        return tripId;
    }

    private ResultActions decide(TestFixture.Employee approver, String workType, long targetId, String action,
                                 String body) throws Exception {
        long stepId = jdbc.queryForObject("""
                        SELECT id FROM approval_step WHERE company_id = ? AND work_type = ?::approval_work_type
                          AND target_id = ? ORDER BY round DESC, step_order LIMIT 1
                        """,
                Long.class, cid, workType, targetId);
        return as(approver.id(), post("/api/approvals/" + stepId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void approve(TestFixture.Employee approver, String workType, long targetId) throws Exception {
        decide(approver, workType, targetId, "approve", "{}").andExpect(status().isOk());
    }

    private long upload(TestFixture.Employee e) throws Exception {
        return id(mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "영수증.png", "image/png", PNG))
                        .param("purpose", "RECEIPT").header("Authorization", fixture.token(e.id(), cid)))
                .andExpect(status().isCreated()));
    }

    private ResultActions claim(TestFixture.Employee e, long tripId, String lines) throws Exception {
        return as(e.id(), post("/api/me/expense-claims").contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessTripId\": %d, \"lines\": [%s]}".formatted(tripId, lines)));
    }

    private static String line(long typeId, String date, long amount, Long fileId) {
        return "{\"expenseTypeId\": %d, \"usedDate\": \"%s\", \"amount\": %d, \"description\": \"내용\", \"receiptFileId\": %s}"
                .formatted(typeId, date, amount, fileId);
    }

    private long claimOk() throws Exception {
        long receipt = upload(org.cho);
        return id(claim(org.cho, trip, line(transport, "2030-03-04", 119800, receipt) + ","
                + line(perDiem, "2030-03-05", 40000, null)).andExpect(status().isCreated()));
    }

    @Test
    void 청구하면_승인대기이고_승인되면_정산_대기가_된다() throws Exception {
        long receipt = upload(org.cho);
        claim(org.cho, trip, line(transport, "2030-03-04", 119800, receipt) + "," + line(perDiem, "2030-03-05", 40000, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(159800))
                .andExpect(jsonPath("$.data.businessTripId").value(trip))
                .andExpect(jsonPath("$.data.destination").value("부산"))
                .andExpect(jsonPath("$.data.settledPayMonth").value(nullValue()))
                .andExpect(jsonPath("$.data.lines[0].expenseTypeName").value("교통비"))
                .andExpect(jsonPath("$.data.lines[0].receiptFile.id").value(receipt))
                .andExpect(jsonPath("$.data.lines[0].receiptFile.originalName").value("영수증.png"))
                .andExpect(jsonPath("$.data.lines[1].receiptFile").value(nullValue()))
                .andExpect(jsonPath("$.data.approvalSteps[0].approverId").value(org.seo.id()));
        long id = jdbc.queryForObject("SELECT id FROM expense_claim WHERE business_trip_id = ?", Long.class, trip);

        as(org.seo.id(), get("/api/approvals/inbox"))
                .andExpect(jsonPath("$.data.content[0].workType").value("TRIP_EXPENSE"))
                .andExpect(jsonPath("$.data.content[0].details.businessTripId").value(trip))
                .andExpect(jsonPath("$.data.content[0].details.totalAmount").value(159800))
                .andExpect(jsonPath("$.data.content[0].details.lineCount").value(2))
                .andExpect(jsonPath("$.data.content[0].details.startDate").value("2030-03-04"));
        approve(org.seo, "TRIP_EXPENSE", id);

        as(org.cho.id(), get("/api/expense-claims/" + id)).andExpect(jsonPath("$.data.status").value("APPROVED"));
        as(org.cho.id(), get("/api/business-trips/" + trip))
                .andExpect(jsonPath("$.data.expenseClaim.id").value(id))
                .andExpect(jsonPath("$.data.expenseClaim.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.expenseClaim.totalAmount").value(159800));
        as(org.company.adminId(), get("/api/expense-claims?settled=false"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].lineCount").value(2))
                .andExpect(jsonPath("$.data.content[0].settledPayMonth").value(nullValue()));
    }

    @Test
    void 영수증_필수_종류에_파일이_없으면_거부한다() throws Exception {
        claim(org.cho, trip, line(perDiem, "2030-03-04", 40000, null) + "," + line(transport, "2030-03-04", 1000, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EXPENSE_RECEIPT_REQUIRED"))
                .andExpect(jsonPath("$.error.details.lineIndex").value(1))
                .andExpect(jsonPath("$.error.details.expenseTypeName").value("교통비"));
    }

    @Test
    void 사용일_영수증_파일_금액이_틀리면_줄별로_알려준다() throws Exception {
        long mine = upload(org.cho);
        long others = upload(org.seo);
        claim(org.cho, trip, line(transport, "2030-03-06", 1000, others) + "," + line(transport, "2030-03-04", 1000, mine)
                + "," + line(transport, "2030-03-04", 1000, mine))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['lines[0].usedDate']").exists())
                .andExpect(jsonPath("$.error.fields['lines[0].receiptFileId']").exists())
                .andExpect(jsonPath("$.error.fields['lines[1].receiptFileId']").doesNotExist())
                .andExpect(jsonPath("$.error.fields['lines[2].receiptFileId']").exists());
        claim(org.cho, trip, line(perDiem, "2030-03-04", 0, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['lines[0].amount']").exists());
        claim(org.cho, trip, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.lines").exists());
    }

    @Test
    void 비활성_종류는_고를_수_없다() throws Exception {
        jdbc.update("UPDATE expense_type SET is_active = FALSE WHERE id = ?", perDiem);
        claim(org.cho, trip, line(perDiem, "2030-03-04", 40000, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
    }

    @Test
    void 내_승인된_출장의_시작일부터_청구한다() throws Exception {
        long pendingTrip = id(as(org.cho.id(), post("/api/me/business-trips").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tripType": "DOMESTIC", "destination": "대구", "purpose": "교육", "startDate": "2030-03-02", "endDate": "2030-03-02"}
                        """)).andExpect(status().isCreated()));
        claim(org.cho, pendingTrip, line(perDiem, "2030-03-02", 40000, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        long future = approvedTrip(org.cho, "2030-03-11", "2030-03-12");
        claim(org.cho, future, line(perDiem, "2030-03-11", 40000, null))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        clock.set(MONDAY.plusDays(7).atTime(8, 0));
        claim(org.cho, future, line(perDiem, "2030-03-11", 40000, null)).andExpect(status().isCreated());

        claim(org.seo, trip, line(perDiem, "2030-03-04", 40000, null)).andExpect(status().isNotFound());
    }

    @Test
    void 출장마다_진행_중_청구는_하나이고_반려되면_다시_청구한다() throws Exception {
        long id = claimOk();
        claim(org.cho, trip, line(perDiem, "2030-03-04", 40000, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXPENSE_CLAIM_DUPLICATE"));

        decide(org.seo, "TRIP_EXPENSE", id, "reject", "{\"comment\": \"영수증 확인 필요\"}").andExpect(status().isOk());
        as(org.cho.id(), get("/api/me/business-trips"))
                .andExpect(jsonPath("$.data.content[0].expenseClaimStatus").value("REJECTED"));
        claim(org.cho, trip, line(perDiem, "2030-03-04", 40000, null)).andExpect(status().isCreated());
        as(org.cho.id(), get("/api/me/business-trips"))
                .andExpect(jsonPath("$.data.content[0].expenseClaimStatus").value("PENDING"));
    }

    @Test
    void 승인대기_중에만_본인이_철회한다() throws Exception {
        long id = claimOk();
        as(org.seo.id(), post("/api/me/expense-claims/" + id + "/withdraw")).andExpect(status().isNotFound());
        as(org.cho.id(), post("/api/me/expense-claims/" + id + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.approvalSteps[0].status").value("CANCELLED"));
        as(org.cho.id(), post("/api/me/expense-claims/" + id + "/withdraw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 상세_목록_영수증은_청구자_승인자_조회_범위_안만_본다() throws Exception {
        long receipt = upload(org.cho);
        long id = id(claim(org.cho, trip, line(transport, "2030-03-04", 119800, receipt)).andExpect(status().isCreated()));
        TestFixture.Employee backend = fixture.employee(cid, org.backendTeam, "직원", false);

        for (long viewer : new long[]{org.cho.id(), org.seo.id(), org.kang.id(), org.company.adminId(), org.ceo.id()}) {
            as(viewer, get("/api/expense-claims/" + id)).andExpect(status().isOk());
            as(viewer, get("/api/files/" + receipt)).andExpect(status().isOk());
        }
        as(org.yoon.id(), get("/api/expense-claims/" + id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
        as(org.yoon.id(), get("/api/files/" + receipt)).andExpect(status().isNotFound());
        as(backend.id(), get("/api/files/" + receipt)).andExpect(status().isNotFound());

        as(org.cho.id(), get("/api/me/expense-claims"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(119800))
                .andExpect(jsonPath("$.data.content[0].currentStep.status").value("PENDING"));
        as(org.seo.id(), get("/api/expense-claims")).andExpect(jsonPath("$.data.totalElements").value(1));
        as(org.yoon.id(), get("/api/expense-claims")).andExpect(jsonPath("$.data.totalElements").value(0));
        as(org.company.adminId(), get("/api/expense-claims?settled=false"))
                .andExpect(jsonPath("$.data.totalElements").value(0));                    // 승인 전은 정산 대기가 아니다
        as(org.company.adminId(), get("/api/expense-claims?orgUnitId=" + org.backendTeam))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 퇴직_처리는_승인대기_청구만_취소하고_대기_건수는_승인대기만_센다() throws Exception {
        long pending = claimOk();
        TenantContext.set(cid);
        try {
            assertThat(expenseClaimService.countPending(cid, org.cho.id())).isEqualTo(1);
            int cancelled = tx.execute(s -> expenseClaimService.cancelPendingByResignation(cid, org.cho.id()));
            assertThat(cancelled).isEqualTo(1);
            assertThat(expenseClaimService.countPending(cid, org.cho.id())).isZero();
        } finally {
            TenantContext.clear();
        }
        assertThat(jdbc.queryForObject("SELECT status::text || ':' || cancel_reason FROM expense_claim WHERE id = ?",
                String.class, pending)).isEqualTo("CANCELLED:퇴직");
    }
}
