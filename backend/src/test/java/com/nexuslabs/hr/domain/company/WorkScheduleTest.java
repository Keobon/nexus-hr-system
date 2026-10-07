package com.nexuslabs.hr.domain.company;

import com.nexuslabs.hr.global.config.ClockConfig;
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

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-COMP-04 근무시간 설정 (API 설계서 3장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkScheduleTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    LocalDate today;

    @BeforeEach
    void setUp() {
        company = fixture.company("근무시간테스트");
        today = LocalDate.now(ClockConfig.ZONE);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private ResultActions create(LocalDate effectiveFrom, String extra) throws Exception {
        return asAdmin(post("/api/work-schedules").contentType(MediaType.APPLICATION_JSON).content("""
                {"effectiveFrom": "%s", "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60,
                 "lateGraceMinutes": 10, "workDays": ["MON", "TUE", "WED", "THU", "FRI", "SAT"]%s}
                """.formatted(effectiveFrom, extra.isEmpty() ? "" : ", " + extra)));
    }

    private long scheduleId(LocalDate effectiveFrom) {
        return jdbc.queryForObject("SELECT id FROM work_schedule WHERE company_id = ? AND effective_from = ?",
                Long.class, company.id(), Date.valueOf(effectiveFrom));
    }

    private int scheduleCount() {
        return jdbc.queryForObject("SELECT count(*) FROM work_schedule WHERE company_id = ?", Integer.class,
                company.id());
    }

    private String staffToken() {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        return fixture.token(staff.id(), company.id());
    }

    // ---------------------------------------------------------------- 조회

    @Test
    void 회사를_등록하면_기본_근무시간이_적용_중이고_모든_직원이_본다() throws Exception {
        mvc.perform(get("/api/work-schedules").header("Authorization", staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.effectiveFrom").value(today.toString()))
                .andExpect(jsonPath("$.data.current.startTime").value("09:00"))
                .andExpect(jsonPath("$.data.current.endTime").value("18:00"))
                .andExpect(jsonPath("$.data.current.breakMinutes").value(60))
                .andExpect(jsonPath("$.data.current.lateGraceMinutes").value(0))
                .andExpect(jsonPath("$.data.current.nightStart").value("22:00"))
                .andExpect(jsonPath("$.data.current.nightEnd").value("06:00"))
                .andExpect(jsonPath("$.data.current.overtimeApprovalRequired").value(true))
                .andExpect(jsonPath("$.data.current.workDays").value(contains("MON", "TUE", "WED", "THU", "FRI")))
                .andExpect(jsonPath("$.data.current.dailyStandardMinutes").value(480))
                .andExpect(jsonPath("$.data.upcoming.length()").value(0))
                .andExpect(jsonPath("$.data.history.length()").value(0));
    }

    @Test
    void 적용_예정과_지난_이력을_나눠서_보여준다() throws Exception {
        // 회사 등록 전부터 쓰던 기준(지난 이력) 하나와 앞으로 바뀔 기준(예정) 두 개
        jdbc.update("""
                INSERT INTO work_schedule (company_id, effective_from, start_time, end_time, break_minutes, work_days)
                VALUES (?, ?, ?::time, ?::time, 60, 31)
                """, company.id(), Date.valueOf("2020-01-01"), "10:00", "19:00");
        create(today.plusDays(30), "").andExpect(status().isCreated());
        create(today.plusDays(10), "").andExpect(status().isCreated());

        asAdmin(get("/api/work-schedules"))
                .andExpect(jsonPath("$.data.current.startTime").value("09:00"))
                .andExpect(jsonPath("$.data.upcoming[*].effectiveFrom")
                        .value(contains(today.plusDays(10).toString(), today.plusDays(30).toString())))
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andExpect(jsonPath("$.data.history[0].effectiveFrom").value("2020-01-01"))
                .andExpect(jsonPath("$.data.history[0].startTime").value("10:00"));
    }

    // ---------------------------------------------------------------- 등록

    @Test
    void 새_근무시간은_행을_추가하고_비운_값은_기본값이다() throws Exception {
        create(today.plusDays(7), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.effectiveFrom").value(today.plusDays(7).toString()))
                .andExpect(jsonPath("$.data.startTime").value("08:00"))
                .andExpect(jsonPath("$.data.endTime").value("17:00"))
                .andExpect(jsonPath("$.data.lateGraceMinutes").value(10))
                .andExpect(jsonPath("$.data.nightStart").value("22:00"))
                .andExpect(jsonPath("$.data.nightEnd").value("06:00"))
                .andExpect(jsonPath("$.data.overtimeApprovalRequired").value(true))
                .andExpect(jsonPath("$.data.workDays").value(contains("MON", "TUE", "WED", "THU", "FRI", "SAT")))
                .andExpect(jsonPath("$.data.dailyStandardMinutes").value(480));

        // 기존 행은 그대로 남는다. 요일은 DB 에 비트값(월–토 = 63)으로 저장된다
        assertThat(scheduleCount()).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT work_days::int AS work_days, created_by FROM work_schedule WHERE id = ?",
                scheduleId(today.plusDays(7))))
                .containsEntry("work_days", 63).containsEntry("created_by", company.adminId());
    }

    @Test
    void 야간_시간대와_연장근무_승인_여부를_지정할_수_있다() throws Exception {
        create(today.plusDays(3), "\"nightStart\": \"23:00\", \"nightEnd\": \"05:00\", \"overtimeApprovalRequired\": false")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nightStart").value("23:00"))
                .andExpect(jsonPath("$.data.nightEnd").value("05:00"))
                .andExpect(jsonPath("$.data.overtimeApprovalRequired").value(false));
    }

    @Test
    void 등록_거부_과거_시작일_같은_시작일() throws Exception {
        create(today.minusDays(1), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_PAST_DATE"));
        // 회사 등록 때 만든 기본 근무시간의 적용 시작일이 오늘이다
        create(today, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        create(today.plusDays(5), "").andExpect(status().isCreated());
        create(today.plusDays(5), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 등록_거부_잘못된_시각_휴게시간_요일() throws Exception {
        create(today.plusDays(1), "\"startTime\": \"18:00\", \"endTime\": \"09:00\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.endTime").exists());
        create(today.plusDays(1), "\"breakMinutes\": 540")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.breakMinutes").exists());
        create(today.plusDays(1), "\"workDays\": []")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.workDays").exists());
        create(today.plusDays(1), "\"workDays\": [\"FUNDAY\"]")
                .andExpect(status().isBadRequest());
        create(today.plusDays(1), "\"nightStart\": \"22:00\", \"nightEnd\": \"22:00\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.nightEnd").exists());
        asAdmin(post("/api/work-schedules").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.effectiveFrom").exists())
                .andExpect(jsonPath("$.error.fields.startTime").exists());
        assertThat(scheduleCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 삭제

    @Test
    void 아직_시작하지_않은_근무시간만_삭제할_수_있다() throws Exception {
        create(today.plusDays(14), "").andExpect(status().isCreated());
        long upcoming = scheduleId(today.plusDays(14));
        long current = scheduleId(today);

        asAdmin(delete("/api/work-schedules/" + current))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        asAdmin(delete("/api/work-schedules/" + upcoming))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        asAdmin(get("/api/work-schedules"))
                .andExpect(jsonPath("$.data.current.id").value(current))
                .andExpect(jsonPath("$.data.upcoming.length()").value(0));
        asAdmin(delete("/api/work-schedules/" + upcoming)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 감사 로그 · 권한 · 회사 격리

    @Test
    void 등록과_삭제는_감사_로그로_남는다() throws Exception {
        create(today.plusDays(2), "").andExpect(status().isCreated());
        long id = scheduleId(today.plusDays(2));
        asAdmin(delete("/api/work-schedules/" + id)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                SELECT action::text FROM audit_log
                WHERE company_id = ? AND target_type = ? AND target_id = ? AND actor_id = ? ORDER BY id
                """, String.class, company.id(), "WORK_SCHEDULE", id, company.adminId()))
                .containsExactly("CREATE", "DELETE");
    }

    @Test
    void 등록과_삭제는_COMPANY_MANAGE_가_있어야_한다() throws Exception {
        create(today.plusDays(4), "").andExpect(status().isCreated());
        String staffToken = staffToken();

        mvc.perform(post("/api/work-schedules").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/work-schedules/" + scheduleId(today.plusDays(4))).header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        assertThat(scheduleCount()).isEqualTo(2);
    }

    @Test
    void 다른_회사의_근무시간은_보이지_않고_삭제는_404() throws Exception {
        create(today.plusDays(9), "").andExpect(status().isCreated());
        long id = scheduleId(today.plusDays(9));
        TestFixture.Company other = fixture.company("근무시간테스트타사");
        String otherToken = fixture.token(other.adminId(), other.id());

        mvc.perform(get("/api/work-schedules").header("Authorization", otherToken))
                .andExpect(jsonPath("$.data.current.startTime").value("09:00"))
                .andExpect(jsonPath("$.data.upcoming.length()").value(0));
        mvc.perform(delete("/api/work-schedules/" + id).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        // 다른 회사는 같은 적용 시작일로 등록할 수 있다
        mvc.perform(post("/api/work-schedules").contentType(MediaType.APPLICATION_JSON).content("""
                        {"effectiveFrom": "%s", "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60,
                         "lateGraceMinutes": 0, "workDays": ["MON"]}
                        """.formatted(today.plusDays(9))).header("Authorization", otherToken))
                .andExpect(status().isCreated());

        asAdmin(get("/api/work-schedules"))
                .andExpect(jsonPath("$.data.upcoming.length()").value(1))
                .andExpect(jsonPath("$.data.upcoming[0].id").value(id));
    }
}
