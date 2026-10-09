package com.nexuslabs.hr.domain.company;

import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-COMP-06 감사 로그 조회(API 설계서 3장 GET /audit-logs). 기록은 JDBC 로 직접 넣고 시각을 정해 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee staff;

    @BeforeEach
    void setUp() {
        company = fixture.company("감사회사");
        staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        // 서울 시각 기준: 3/1 23:30(= UTC 3/1 14:30), 3/2 00:10(= UTC 3/1 15:10), 3/3 09:00
        log(company.id(), company.adminId(), "UPDATE", "T_ROLE", 7L, "{\"name\": \"전\"}", "{\"name\": \"후\"}",
                "2030-03-01 23:30+09");
        log(company.id(), null, "EXECUTE", "T_BATCH", null, null, "{\"created\": 3}", "2030-03-02 00:10+09");
        log(company.id(), staff.id(), "CREATE", "T_ROLE", 8L, null, "{\"name\": \"새 역할\"}", "2030-03-03 09:00+09");
        // 다른 회사 기록은 안 보인다
        TestFixture.Company other = fixture.company("다른회사");
        log(other.id(), other.adminId(), "UPDATE", "T_ROLE", 9L, null, null, "2030-03-02 10:00+09");
    }

    private void log(long companyId, Long actorId, String action, String targetType, Long targetId, String before,
                     String after, String createdAt) {
        jdbc.update("""
                        INSERT INTO audit_log (company_id, actor_id, action, target_type, target_id, before_value,
                                               after_value, created_at)
                        VALUES (?, ?, ?::audit_action, ?, ?, ?::jsonb, ?::jsonb, ?::timestamptz)
                        """,
                companyId, actorId, action, targetType, targetId, before, after, createdAt);
    }

    private ResultActions list(TestFixture.Employee... as) throws Exception {
        return list("from=2030-03-01&to=2030-03-31", as);
    }

    private ResultActions list(String query, TestFixture.Employee... as) throws Exception {
        long who = as.length == 0 ? company.adminId() : as[0].id();
        return mvc.perform(get("/api/audit-logs?" + query).header("Authorization", fixture.token(who, company.id())));
    }

    @Test
    void 최신순이고_실행자_이름과_변경_전후_값이_온다() throws Exception {
        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].targetType").value(contains("T_ROLE", "T_BATCH", "T_ROLE")))
                .andExpect(jsonPath("$.data.content[0].actorId").value(staff.id()))
                .andExpect(jsonPath("$.data.content[0].actorName").exists())
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2030-03-03T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.content[0].before").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].after.name").value("새 역할"))
                // 시스템 처리는 실행자 없음
                .andExpect(jsonPath("$.data.content[1].actorId").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].actorName").doesNotExist())
                .andExpect(jsonPath("$.data.content[2].before.name").value("전"))
                .andExpect(jsonPath("$.data.content[2].after.name").value("후"))
                .andExpect(jsonPath("$.data.content[2].targetId").value(7));
    }

    @Test
    void 날짜는_서울_기준_양_끝_포함() throws Exception {
        list("from=2030-03-02&to=2030-03-02")
                .andExpect(jsonPath("$.data.content[*].targetType").value(contains("T_BATCH")));
        list("from=2030-03-01&to=2030-03-01")
                .andExpect(jsonPath("$.data.content[*].targetId").value(contains(7)));
        list("from=2030-03-03&to=2030-03-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.to").exists());
    }

    @Test
    void 실행자_대상_종류_행위로_거른다() throws Exception {
        list("targetType=T_ROLE&from=2030-03-01")
                .andExpect(jsonPath("$.data.content[*].targetId").value(contains(8, 7)));
        list("actorId=" + staff.id() + "&from=2030-03-01")
                .andExpect(jsonPath("$.data.content[*].targetId").value(contains(8)));
        list("action=EXECUTE&from=2030-03-01")
                .andExpect(jsonPath("$.data.content[*].targetType").value(contains("T_BATCH")));
        list("action=REMOVE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));
        // 페이징
        list("from=2030-03-01&size=2&page=1")
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].targetId").value(contains(7)));
    }

    @Test
    void AUDIT_READ_가_없으면_403() throws Exception {
        list(staff).andExpect(status().isForbidden());
    }
}
