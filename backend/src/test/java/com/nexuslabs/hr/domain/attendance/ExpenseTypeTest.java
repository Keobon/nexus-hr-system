package com.nexuslabs.hr.domain.attendance;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ATT-09 출장 경비 종류 관리 (API 설계서 7.3).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExpenseTypeTest {

    private static final String PATH = "/api/expense-types";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("경비종류테스트");
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return as(company.adminId(), request);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long typeId(String name) {
        return jdbc.queryForObject("SELECT id FROM expense_type WHERE company_id = ? AND name = ?",
                Long.class, company.id(), name);
    }

    private long auditCount(long id, String action) {
        return jdbc.queryForObject("""
                        SELECT count(*) FROM audit_log
                        WHERE company_id = ? AND target_type = 'EXPENSE_TYPE' AND target_id = ? AND action = ?::audit_action
                        """,
                Long.class, company.id(), id, action);
    }

    @Test
    void 회사_등록_때_만든_기본_종류가_정렬_순서대로_나온다() throws Exception {
        asAdmin(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("교통비", "숙박비", "식비", "일비", "기타")))
                .andExpect(jsonPath("$.data[0].receiptRequired").value(true))
                .andExpect(jsonPath("$.data[3].receiptRequired").value(false))
                .andExpect(jsonPath("$.data[0].isActive").value(true));
    }

    @Test
    void 등록하면_비운_값은_영수증_필수_맨_뒤_활성이고_감사_로그가_남는다() throws Exception {
        asAdmin(json(post(PATH), "{\"name\": \" 통신비 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("통신비"))
                .andExpect(jsonPath("$.data.receiptRequired").value(true))
                .andExpect(jsonPath("$.data.sortOrder").value(6))
                .andExpect(jsonPath("$.data.isActive").value(true));
        assertThat(auditCount(typeId("통신비"), "CREATE")).isEqualTo(1);

        asAdmin(json(post(PATH), "{\"name\": \"주차비\", \"receiptRequired\": false, \"sortOrder\": 0, \"isActive\": false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.receiptRequired").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void 이름이_없거나_겹치면_거부한다() throws Exception {
        asAdmin(json(post(PATH), "{\"name\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists());
        asAdmin(json(post(PATH), "{\"name\": \"교통비\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        asAdmin(json(patch(PATH + "/" + typeId("숙박비")), "{\"name\": \"교통비\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
    }

    @Test
    void 수정은_보낸_필드만_바꾸고_감사_로그를_남긴다() throws Exception {
        long id = typeId("식비");

        asAdmin(json(patch(PATH + "/" + id), "{\"receiptRequired\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("식비"))
                .andExpect(jsonPath("$.data.receiptRequired").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(3))
                .andExpect(jsonPath("$.data.isActive").value(true));
        asAdmin(json(patch(PATH + "/" + id), "{\"name\": \"식대\", \"sortOrder\": 9, \"isActive\": false}"))
                .andExpect(jsonPath("$.data.name").value("식대"))
                .andExpect(jsonPath("$.data.receiptRequired").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(9))
                .andExpect(jsonPath("$.data.isActive").value(false));
        assertThat(auditCount(id, "UPDATE")).isEqualTo(2);

        asAdmin(get(PATH + "?activeOnly=true")).andExpect(jsonPath("$.data[*].name").value(not(hasItem("식대"))));
        asAdmin(get(PATH)).andExpect(jsonPath("$.data[4].name").value("식대"));
    }

    @Test
    void 수정에_모르는_필드나_null_이나_틀린_형식을_보내면_거부한다() throws Exception {
        long id = typeId("기타");

        asAdmin(json(patch(PATH + "/" + id), "{\"color\": \"red\", \"name\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.color").exists())
                .andExpect(jsonPath("$.error.fields.name").exists());
        asAdmin(json(patch(PATH + "/" + id), "{\"receiptRequired\": \"yes\", \"sortOrder\": \"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.receiptRequired").exists())
                .andExpect(jsonPath("$.error.fields.sortOrder").exists());
    }

    @Test
    void 쓰인_적_없는_종류는_삭제되고_청구에_쓰인_종류는_비활성화된다() throws Exception {
        long unused = typeId("일비");
        asAdmin(delete(PATH + "/" + unused))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DELETED"));
        asAdmin(get(PATH)).andExpect(jsonPath("$.data[*].name").value(not(hasItem("일비"))));
        assertThat(auditCount(unused, "DELETE")).isEqualTo(1);

        long used = typeId("교통비");
        long tripId = jdbc.queryForObject("""
                        INSERT INTO business_trip (company_id, employee_id, trip_type, destination, purpose, start_date, end_date, status)
                        VALUES (?, ?, 'DOMESTIC', '부산', '고객사 방문', DATE '2030-03-04', DATE '2030-03-05', 'APPROVED')
                        RETURNING id
                        """,
                Long.class, company.id(), company.adminId());
        long claimId = jdbc.queryForObject(
                "INSERT INTO expense_claim (company_id, business_trip_id, employee_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, company.id(), tripId, company.adminId());
        jdbc.update("""
                        INSERT INTO expense_claim_line (company_id, expense_claim_id, expense_type_id, used_date, amount)
                        VALUES (?, ?, ?, DATE '2030-03-04', 59900)
                        """,
                company.id(), claimId, used);

        asAdmin(delete(PATH + "/" + used))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        asAdmin(get(PATH)).andExpect(jsonPath("$.data[0].name").value("교통비"))
                .andExpect(jsonPath("$.data[0].isActive").value(false));
    }

    @Test
    void 조회는_누구나_관리는_ATTENDANCE_MANAGE_만_할_수_있다() throws Exception {
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);

        as(staff.id(), get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5));
        as(staff.id(), json(post(PATH), "{\"name\": \"통신비\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        as(staff.id(), json(patch(PATH + "/" + typeId("기타")), "{\"sortOrder\": 1}")).andExpect(status().isForbidden());
        as(staff.id(), delete(PATH + "/" + typeId("기타"))).andExpect(status().isForbidden());
    }

    @Test
    void 다른_회사의_종류는_보이지_않고_수정_삭제는_404() throws Exception {
        TestFixture.Company other = fixture.company("경비종류테스트타사");
        long otherId = jdbc.queryForObject("SELECT id FROM expense_type WHERE company_id = ? AND name = '기타'",
                Long.class, other.id());

        asAdmin(json(patch(PATH + "/" + otherId), "{\"sortOrder\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        asAdmin(delete(PATH + "/" + otherId)).andExpect(status().isNotFound());
        asAdmin(get(PATH)).andExpect(jsonPath("$.data.length()").value(5));
    }
}
