package com.nexuslabs.hr.domain.org;

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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ORG-03·04 직급 · 직책 · 고용형태 관리 (API 설계서 5장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrgSettingItemTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;

    @BeforeEach
    void setUp() {
        company = fixture.company("직급테스트");
        // 회사 등록 때 들어가는 기본 직급(부록 B)을 지우고 빈 상태에서 시작한다
        jdbc.update("DELETE FROM job_grade WHERE company_id = ?", company.id());
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long create(String path, String name, int sortOrder) throws Exception {
        asAdmin(json(post(path), "{\"name\": \"%s\", \"sortOrder\": %d}".formatted(name, sortOrder)))
                .andExpect(status().isCreated());
        return itemId(path, name);
    }

    private long itemId(String path, String name) {
        String table = path.substring("/api/".length(), path.length() - 1).replace('-', '_');
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE company_id = ? AND name = ?",
                Long.class, company.id(), name);
    }

    @Test
    void 직급을_등록하면_정렬_순서대로_나온다() throws Exception {
        asAdmin(json(post("/api/job-grades"), "{\"name\": \" 대리 \", \"sortOrder\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("대리"))
                .andExpect(jsonPath("$.data.sortOrder").value(2))
                .andExpect(jsonPath("$.data.isActive").value(true));
        create("/api/job-grades", "사원", 1);

        asAdmin(get("/api/job-grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("사원", "대리")));
    }

    @Test
    void 수정은_이름과_순서를_바꾸고_isActive_를_안_보내면_그대로() throws Exception {
        long id = create("/api/job-titles", "팀장", 1);

        asAdmin(json(patch("/api/job-titles/" + id), "{\"name\": \"파트장\", \"sortOrder\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("파트장"))
                .andExpect(jsonPath("$.data.sortOrder").value(5))
                .andExpect(jsonPath("$.data.isActive").value(true));
        asAdmin(json(patch("/api/job-titles/" + id), "{\"name\": \"파트장\", \"sortOrder\": 5, \"isActive\": false}"))
                .andExpect(jsonPath("$.data.isActive").value(false));

        asAdmin(get("/api/job-titles")).andExpect(jsonPath("$.data.length()").value(1));
        asAdmin(get("/api/job-titles?activeOnly=true")).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 회사_안에서_같은_이름은_거부한다() throws Exception {
        create("/api/job-grades", "과장", 1);
        long other = create("/api/job-grades", "차장", 2);

        asAdmin(json(post("/api/job-grades"), "{\"name\": \"과장\", \"sortOrder\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        asAdmin(json(patch("/api/job-grades/" + other), "{\"name\": \"과장\", \"sortOrder\": 2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        // 직급과 직책은 별도 목록이라 같은 이름을 쓸 수 있다
        asAdmin(json(post("/api/job-titles"), "{\"name\": \"과장\", \"sortOrder\": 1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 이름이나_순서가_없으면_입력값_오류() throws Exception {
        asAdmin(json(post("/api/job-grades"), "{\"name\": \" \", \"sortOrder\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").exists());
        asAdmin(json(post("/api/job-grades"), "{\"name\": \"부장\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.sortOrder").exists());
    }

    @Test
    void 쓰인_적_없는_직급은_삭제되고_직원이_쓰는_직급은_비활성화된다() throws Exception {
        long unused = create("/api/job-grades", "수석", 1);
        long used = create("/api/job-grades", "책임", 2);
        jdbc.update("UPDATE employee SET job_grade_id = ? WHERE id = ?", used, company.adminId());

        asAdmin(delete("/api/job-grades/" + unused))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DELETED"));
        asAdmin(delete("/api/job-grades/" + used))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));

        asAdmin(get("/api/job-grades"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("책임"))
                .andExpect(jsonPath("$.data[0].isActive").value(false));
    }

    @Test
    void 승인선_단계나_조건에_쓰인_직책은_비활성화된다() throws Exception {
        long inStep = create("/api/job-titles", "본부장", 1);
        long inCondition = create("/api/job-titles", "실장", 2);
        long lineId = jdbc.queryForObject(
                "SELECT id FROM approval_line WHERE company_id = ? AND work_type = 'LEAVE' AND is_default",
                Long.class, company.id());
        jdbc.update("""
                INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type, job_title_id)
                VALUES (?, ?, 2, 'JOB_TITLE', ?)
                """, company.id(), lineId, inStep);
        jdbc.update("""
                INSERT INTO approval_line (company_id, name, work_type, cond_job_title_id, priority)
                VALUES (?, '실장 휴가', 'LEAVE', ?, 1)
                """, company.id(), inCondition);

        asAdmin(delete("/api/job-titles/" + inStep)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        asAdmin(delete("/api/job-titles/" + inCondition)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
    }

    @Test
    void 고용형태는_기본_3개가_있고_직원이_쓰는_것은_비활성화된다() throws Exception {
        asAdmin(get("/api/employment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("정규직", "계약직", "인턴")));

        // 회사 등록 때 만든 관리자가 정규직이다
        asAdmin(delete("/api/employment-types/" + itemId("/api/employment-types", "정규직")))
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        asAdmin(delete("/api/employment-types/" + itemId("/api/employment-types", "인턴")))
                .andExpect(jsonPath("$.data.result").value("DELETED"));

        asAdmin(get("/api/employment-types?activeOnly=true"))
                .andExpect(jsonPath("$.data[*].name").value(contains("계약직")));
    }

    @Test
    void 조회는_로그인만_하면_되고_관리는_ORG_MANAGE_가_있어야_한다() throws Exception {
        long id = create("/api/job-grades", "주임", 1);
        TestFixture.Employee staff = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        String staffToken = fixture.token(staff.id(), company.id());

        mvc.perform(get("/api/job-grades").header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(json(post("/api/job-grades"), "{\"name\": \"선임\", \"sortOrder\": 2}")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/job-grades/" + id).header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회사의_항목은_보이지_않고_수정_삭제는_404() throws Exception {
        long id = create("/api/job-grades", "이사", 1);
        TestFixture.Company other = fixture.company("직급테스트타사");
        String otherToken = fixture.token(other.adminId(), other.id());

        mvc.perform(get("/api/job-grades").header("Authorization", otherToken))
                .andExpect(jsonPath("$.data.length()").value(5));    // 그 회사의 기본 직급만
        mvc.perform(json(patch("/api/job-grades/" + id), "{\"name\": \"상무\", \"sortOrder\": 1}")
                        .header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(delete("/api/job-grades/" + id).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        // 같은 이름도 회사가 다르면 쓸 수 있다
        mvc.perform(json(post("/api/job-grades"), "{\"name\": \"이사\", \"sortOrder\": 1}")
                        .header("Authorization", otherToken))
                .andExpect(status().isCreated());
    }
}
