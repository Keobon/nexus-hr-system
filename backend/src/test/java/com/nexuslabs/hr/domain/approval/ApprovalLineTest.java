package com.nexuslabs.hr.domain.approval;

import com.nexuslabs.hr.support.DemoOrg;
import com.nexuslabs.hr.support.TestFixture;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 승인선 관리 (F-APPR-01, API 설계서 9.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalLineTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    DemoOrg org;

    @BeforeEach
    void setUp() {
        org = new DemoOrg(fixture, jdbc);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.header("Authorization", fixture.token(org.company.adminId(), org.company.id())));
    }

    private ResultActions send(MockHttpServletRequestBuilder r, String body) throws Exception {
        return asAdmin(r.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long defaultLine(String workType) {
        return jdbc.queryForObject("SELECT id FROM approval_line WHERE company_id = ? AND work_type = ?::approval_work_type AND is_default",
                Long.class, org.company.id(), workType);
    }

    private String line(String condition, String steps) {
        return """
                {"name": "팀장 출장", "workType": "BUSINESS_TRIP", %s, "priority": 1, "isActive": true, "steps": %s}
                """.formatted(condition, steps);
    }

    @Test
    void 목록은_우선순위_순이고_기본_승인선이_맨_끝() throws Exception {
        asAdmin(get("/api/approval-lines").param("workType", "LEAVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("본부장 휴가"))
                .andExpect(jsonPath("$.data[0].condJobTitleName").value("본부장"))
                .andExpect(jsonPath("$.data[0].steps[0].jobTitleName").value("대표이사"))
                .andExpect(jsonPath("$.data[1].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].steps.length()").value(2));
        mvc.perform(get("/api/approval-lines").header("Authorization", fixture.token(org.cho.id(), org.company.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 승인선을_만들고_단계를_통째로_바꾼다() throws Exception {
        String body = send(post("/api/approval-lines"), line("\"condRoleId\": " + roleId("직원"),
                "[{\"stepOrder\": 1, \"approverType\": \"ORG_LEAD\"}, {\"stepOrder\": 2, \"approverType\": \"EMPLOYEE\", \"employeeId\": " + org.ceo.id() + "}]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.condRoleName").value("직원"))
                .andExpect(jsonPath("$.data.steps[1].employeeName").exists())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"data\":\\{\"id\":(\\d+).*", "$1"));

        send(put("/api/approval-lines/" + id), line("\"condRoleId\": " + roleId("직원"),
                "[{\"stepOrder\": 1, \"approverType\": \"ORG_LEAD_UP\", \"upLevels\": 2}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(1))
                .andExpect(jsonPath("$.data.steps[0].upLevels").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type = 'APPROVAL_LINE' AND target_id = ?",
                Long.class, id)).isEqualTo(2);
    }

    @Test
    void 입력_검증() throws Exception {
        String orgLead = "[{\"stepOrder\": 1, \"approverType\": \"ORG_LEAD\"}]";
        // 조건 없음(기본 승인선만 가능) · 조건 두 개 · 휴가 취소
        send(post("/api/approval-lines"), line("\"condRoleId\": null", orgLead))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.condJobTitleId").exists());
        send(post("/api/approval-lines"), line("\"condRoleId\": " + roleId("직원") + ", \"condJobTitleId\": " + org.ceoTitle, orgLead))
                .andExpect(status().isBadRequest());
        send(post("/api/approval-lines"), line("\"condRoleId\": " + roleId("직원"), orgLead).replace("BUSINESS_TRIP", "LEAVE_CANCEL"))
                .andExpect(status().isBadRequest());
        // 단계 0개 · 6개 · 순서 빈틈 · 값 누락
        send(post("/api/approval-lines"), line("\"condRoleId\": " + roleId("직원"), "[]"))
                .andExpect(jsonPath("$.error.fields.steps").exists());
        send(post("/api/approval-lines"), line("\"condRoleId\": " + roleId("직원"),
                "[{\"stepOrder\": 1, \"approverType\": \"ORG_LEAD\"}, {\"stepOrder\": 3, \"approverType\": \"ORG_LEAD\"}]"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        send(post("/api/approval-lines"), line("\"condRoleId\": " + roleId("직원"),
                "[{\"stepOrder\": 1, \"approverType\": \"JOB_TITLE\"}]"))
                .andExpect(jsonPath("$.error.details.stepOrder").value(1));
        // 다른 회사 직책 → 404
        TestFixture.Company other = fixture.company("다른회사");
        long otherTitle = jdbc.queryForObject("INSERT INTO job_title (company_id, name, sort_order) VALUES (?, '대표', 1) RETURNING id",
                Long.class, other.id());
        send(post("/api/approval-lines"), line("\"condJobTitleId\": " + otherTitle, orgLead)).andExpect(status().isNotFound());
        // 같은 조건의 활성 승인선 중복
        send(post("/api/approval-lines"), line("\"condJobTitleId\": " + org.ceoTitle, orgLead)).andExpect(status().isCreated());
        send(post("/api/approval-lines"), line("\"condJobTitleId\": " + org.ceoTitle, orgLead))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.condJobTitleId").value("같은 조건의 활성 승인선이 이미 있습니다"));
    }

    @Test
    void 기본_승인선은_조건_지정_비활성화_삭제가_안_된다() throws Exception {
        long id = defaultLine("OVERTIME");
        String orgLead = "[{\"stepOrder\": 1, \"approverType\": \"ORG_LEAD\"}]";
        send(put("/api/approval-lines/" + id), """
                {"name": "연장근무 기본", "workType": "OVERTIME", "condJobTitleId": %d, "priority": 100, "isActive": true, "steps": %s}
                """.formatted(org.ceoTitle, orgLead))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPROVAL_DEFAULT_LOCKED"));
        send(put("/api/approval-lines/" + id), """
                {"name": "연장근무 기본", "workType": "OVERTIME", "priority": 100, "isActive": false, "steps": %s}
                """.formatted(orgLead))
                .andExpect(jsonPath("$.error.code").value("APPROVAL_DEFAULT_LOCKED"));
        asAdmin(delete("/api/approval-lines/" + id)).andExpect(jsonPath("$.error.code").value("APPROVAL_DEFAULT_LOCKED"));
        // 이름·단계는 바꿀 수 있다
        send(put("/api/approval-lines/" + id), """
                {"name": "연장근무", "workType": "OVERTIME", "priority": 100, "isActive": true,
                 "steps": [{"stepOrder": 1, "approverType": "ORG_LEAD"}, {"stepOrder": 2, "approverType": "ORG_LEAD_UP", "upLevels": 1}]}
                """).andExpect(status().isOk());
    }

    @Test
    void 쓰인_승인선은_삭제하면_비활성화된다() throws Exception {
        long headLine = jdbc.queryForObject("SELECT id FROM approval_line WHERE company_id = ? AND name = '본부장 휴가'",
                Long.class, org.company.id());
        jdbc.update("""
                INSERT INTO approval_step (company_id, work_type, target_id, approval_line_id, step_order, approver_id, status)
                VALUES (?, 'LEAVE', 1, ?, 1, ?, 'APPROVED')""", org.company.id(), headLine, org.ceo.id());
        asAdmin(delete("/api/approval-lines/" + headLine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        assertThat(jdbc.queryForObject("SELECT is_active FROM approval_line WHERE id = ?", Boolean.class, headLine)).isFalse();

        String body = send(post("/api/approval-lines"), line("\"condJobTitleId\": " + org.ceoTitle,
                "[{\"stepOrder\": 1, \"approverType\": \"ORG_LEAD\"}]")).andReturn().getResponse().getContentAsString();
        long unused = Long.parseLong(body.replaceAll(".*\"data\":\\{\"id\":(\\d+).*", "$1"));
        asAdmin(delete("/api/approval-lines/" + unused)).andExpect(jsonPath("$.data.result").value("DELETED"));
    }

    private long roleId(String name) {
        return jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?", Long.class, org.company.id(), name);
    }
}
