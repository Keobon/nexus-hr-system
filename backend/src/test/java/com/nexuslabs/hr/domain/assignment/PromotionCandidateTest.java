package com.nexuslabs.hr.domain.assignment;

import com.nexuslabs.hr.global.config.ClockConfig;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Date;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 승진 대상자(10/15 시연 피드백) — 지금 직급에 N년(기본 3년) 이상 있은 재직 직원. 직급 시작일은 마지막 직급 변경 발령일(정정된 발령은
 * 빼고), 없으면 입사일. 최근 3년간 확정된 평가 총점 평균을 같이 준다. 확정은 기존 승진 발령으로 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PromotionCandidateTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    final LocalDate today = LocalDate.now(ClockConfig.ZONE);

    TestFixture.Company company;
    long team;
    long hr;
    long staff;
    long senior;
    long question;
    long template;

    @BeforeEach
    void setUp() {
        company = fixture.company("승진대상테스트");
        team = fixture.orgUnit(company.id(), company.rootOrgUnitId(), "백엔드팀");
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false).id();
        staff = grade("사원");
        senior = grade("대리");
        template = jdbc.queryForObject("INSERT INTO eval_template (company_id, name) VALUES (?, '일반') RETURNING id",
                Long.class, company.id());
        long criteria = jdbc.queryForObject("""
                INSERT INTO eval_criteria (company_id, eval_template_id, category, name, weight, sort_order)
                VALUES (?, ?, '성과', '성과', 100, 1) RETURNING id""", Long.class, company.id(), template);
        question = jdbc.queryForObject("""
                INSERT INTO eval_question (company_id, eval_criteria_id, content, sort_order) VALUES (?, ?, '잘했나', 1)
                RETURNING id""", Long.class, company.id(), criteria);
    }

    private long grade(String name) {
        return jdbc.queryForObject("SELECT id FROM job_grade WHERE company_id = ? AND name = ?", Long.class,
                company.id(), name);
    }

    /** 백엔드팀 사원, 입사일 지정. */
    private long employee(String name, LocalDate hireDate) {
        long id = fixture.employee(company.id(), team, "직원", false).id();
        jdbc.update("UPDATE employee SET name = ?, hire_date = ?, job_grade_id = ? WHERE id = ?",
                name, Date.valueOf(hireDate), staff, id);
        return id;
    }

    private long history(long employeeId, String type, long fromGrade, long toGrade, LocalDate date, Long correctionOf) {
        return jdbc.queryForObject("""
                        INSERT INTO assignment_history (company_id, employee_id, assignment_type, from_org_unit_id, to_org_unit_id,
                                                        from_job_grade_id, to_job_grade_id, reason, effective_date,
                                                        correction_of_id, created_by)
                        VALUES (?, ?, ?::assignment_type, ?, ?, ?, ?, '테스트', ?, ?, ?) RETURNING id""",
                Long.class, company.id(), employeeId, type, team, team, fromGrade, toGrade, Date.valueOf(date),
                correctionOf, company.adminId());
    }

    /** 배점 100 · 질문 1개라 점수 × 20 이 총점(4 → 80.0). */
    private void evaluation(long target, LocalDate cycleEnd, int score, String status) {
        long cycle = jdbc.queryForObject("""
                        INSERT INTO eval_cycle (company_id, name, start_date, end_date, status)
                        VALUES (?, ?, ?, ?, 'CLOSED') RETURNING id""",
                Long.class, company.id(), "기간" + cycleEnd + target, Date.valueOf(cycleEnd.minusMonths(6)),
                Date.valueOf(cycleEnd));
        long evaluation = jdbc.queryForObject("""
                        INSERT INTO evaluation (company_id, eval_cycle_id, target_employee_id, evaluator_id, eval_template_id,
                                                status)
                        VALUES (?, ?, ?, ?, ?, ?::evaluation_status) RETURNING id""",
                Long.class, company.id(), cycle, target, company.adminId(), template, status);
        jdbc.update("INSERT INTO eval_answer (company_id, evaluation_id, eval_question_id, score) VALUES (?, ?, ?, ?)",
                company.id(), evaluation, question, score);
    }

    private ResultActions candidates(long viewer, String query) throws Exception {
        return mvc.perform(get("/api/promotions/candidates" + query)
                .header("Authorization", fixture.token(viewer, company.id())));
    }

    @Test
    void 입사_후_3년이_된_날부터_대상이고_직급_시작일과_체류_연수를_준다() throws Exception {
        long just = employee("딱3년", today.minusYears(3));
        employee("하루모자람", today.minusYears(3).plusDays(1));

        candidates(hr, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].name", contains("딱3년")))
                .andExpect(jsonPath("$.data.content[0].employeeId").value(just))
                .andExpect(jsonPath("$.data.content[0].orgUnitName").value("백엔드팀"))
                .andExpect(jsonPath("$.data.content[0].jobGradeName").value("사원"))
                .andExpect(jsonPath("$.data.content[0].gradeSince").value(today.minusYears(3).toString()))
                .andExpect(jsonPath("$.data.content[0].yearsInGrade").value(3));
    }

    @Test
    void 마지막_승진일이_기준이고_직급이_안_바뀐_발령은_기준을_바꾸지_않는다() throws Exception {
        long recent = employee("최근승진", today.minusYears(10));
        history(recent, "PROMOTION", staff, senior, today.minusYears(1), null);
        long old = employee("예전승진", today.minusYears(10));
        history(old, "PROMOTION", staff, senior, today.minusYears(5), null);
        history(old, "TRANSFER", senior, senior, today.minusMonths(1), null);

        candidates(hr, "")
                .andExpect(jsonPath("$.data.content[*].name", contains("예전승진")))
                .andExpect(jsonPath("$.data.content[0].gradeSince").value(today.minusYears(5).toString()))
                .andExpect(jsonPath("$.data.content[0].yearsInGrade").value(5));
    }

    @Test
    void 정정된_승진_발령은_빼고_본다() throws Exception {
        long e = employee("승진취소", today.minusYears(10));
        long promotion = history(e, "PROMOTION", staff, senior, today.minusYears(1), null);
        history(e, "PROMOTION", staff, staff, today.minusYears(1), promotion); // 정정 — 직급이 그대로

        candidates(hr, "")
                .andExpect(jsonPath("$.data.content[*].name", contains("승진취소")))
                .andExpect(jsonPath("$.data.content[0].gradeSince").value(today.minusYears(10).toString()));
    }

    @Test
    void 최근_3년_확정된_평가의_총점_평균과_건수를_준다() throws Exception {
        long e = employee("평가있음", today.minusYears(5));
        evaluation(e, today.minusYears(1), 4, "CONFIRMED");     // 80.0
        evaluation(e, today.minusYears(2), 3, "CONFIRMED");     // 60.0
        evaluation(e, today.minusYears(4), 1, "CONFIRMED");     // 3년 전보다 앞 — 빠짐
        evaluation(e, today.minusMonths(6), 5, "SUBMITTED");    // 확정 전 — 빠짐
        employee("평가없음", today.minusYears(4));

        candidates(hr, "")
                .andExpect(jsonPath("$.data.content[*].name", contains("평가있음", "평가없음")))
                .andExpect(jsonPath("$.data.content[0].averageScore").value(70.0))
                .andExpect(jsonPath("$.data.content[0].evaluationCount").value(2))
                .andExpect(jsonPath("$.data.content[1].averageScore").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].evaluationCount").value(0));
    }

    @Test
    void 휴직_퇴직_직급_없는_직원과_다른_회사_직원은_빠진다() throws Exception {
        long onLeave = employee("휴직중", today.minusYears(5));
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", onLeave);
        long resigned = employee("퇴직함", today.minusYears(5));
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", resigned);
        long noGrade = employee("직급없음", today.minusYears(5));
        jdbc.update("UPDATE employee SET job_grade_id = NULL WHERE id = ?", noGrade);
        employee("재직중", today.minusYears(5));
        TestFixture.Company other = fixture.company("승진대상다른회사");
        long outsider = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false).id();
        jdbc.update("""
                UPDATE employee SET name = '외부인', hire_date = ?,
                       job_grade_id = (SELECT id FROM job_grade WHERE company_id = ? AND name = '사원')
                WHERE id = ?""", Date.valueOf(today.minusYears(5)), other.id(), outsider);

        candidates(hr, "").andExpect(jsonPath("$.data.content[*].name", contains("재직중")));
    }

    @Test
    void 체류_연수를_바꿀_수_있고_1년보다_작으면_입력_오류() throws Exception {
        employee("1년차", today.minusYears(1));
        candidates(hr, "?minYears=1").andExpect(jsonPath("$.data.content[*].name", hasItem("1년차")));
        candidates(hr, "").andExpect(jsonPath("$.data.content[*].name", not(hasItem("1년차"))));
        candidates(hr, "?minYears=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 전사_발령_조회_권한이_없으면_볼_수_없다() throws Exception {
        long lead = employee("팀장", today.minusYears(5));
        fixture.lead(company.id(), team, lead);
        candidates(lead, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
