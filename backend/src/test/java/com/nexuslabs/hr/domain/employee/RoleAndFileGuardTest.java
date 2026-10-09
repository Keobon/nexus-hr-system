package com.nexuslabs.hr.domain.employee;

import com.jayway.jsonpath.JsonPath;
import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 전 보안 항목(역할 분담 v2 2.3 I 69번, 2026-10-10).
 * ① 직원 등록에서 기본 역할이 아닌 역할을 주려면 ROLE_MANAGE ② 프로필 사진 · 로고는 서류와 같은 파일 규칙(BR-FILE-001) —
 * 요청자가 올린 파일만, 이미 다른 곳(서류 · 영수증 · 프로필 사진 · 로고)에 쓰인 파일은 거부.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAndFileGuardTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee admin;
    TestFixture.Employee hr;
    TestFixture.Employee kim;
    long gradeId;
    long typeId;

    @BeforeEach
    void setUp() {
        company = fixture.company("보안점검");
        admin = new TestFixture.Employee(company.adminId(), company.id(), company.adminEmail());
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        gradeId = jdbc.queryForObject("INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, '사원', 1) RETURNING id",
                Long.class, company.id());
        typeId = jdbc.queryForObject("SELECT id FROM employment_type WHERE company_id = ? AND name = '정규직'",
                Long.class, company.id());
    }

    private ResultActions as(TestFixture.Employee e, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(e.id(), company.id())));
    }

    private ResultActions json(TestFixture.Employee e, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return as(e, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long upload(TestFixture.Employee e, String purpose) throws Exception {
        String body = mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "사진.png", null, PNG))
                        .param("purpose", purpose).header("Authorization", fixture.token(e.id(), company.id())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long role(String name) {
        return jdbc.queryForObject("SELECT id FROM role WHERE company_id = ? AND name = ?", Long.class, company.id(), name);
    }

    private ResultActions register(TestFixture.Employee actor, String local, String extra) throws Exception {
        return json(actor, post("/api/employees"), """
                {"name": "신입", "email": "%s.%d@guard.example", "hireDate": "2026-03-03", "orgUnitId": %d,
                 "jobGradeId": %d, "employmentTypeId": %d%s}""".formatted(local, company.id(), company.rootOrgUnitId(),
                gradeId, typeId, extra.isEmpty() ? "" : ", " + extra));
    }

    private boolean registered(String local) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM employee WHERE email = ?)", Boolean.class,
                local + "." + company.id() + "@guard.example");
    }

    @Test
    void 기본_역할이_아닌_역할을_주려면_역할_관리_권한이_필요하다() throws Exception {
        // 인사 담당은 EMPLOYEE_MANAGE 는 있지만 ROLE_MANAGE 는 없다
        register(hr, "boss", "\"roleId\": " + role("최고 관리자"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(registered("boss")).isFalse();
        register(hr, "plain", "\"roleId\": " + role("직원")).andExpect(status().isCreated());
        register(hr, "none", "").andExpect(status().isCreated());
        // 최고 관리자(ROLE_MANAGE 있음)는 어떤 역할이든 줄 수 있다
        register(admin, "hr2", "\"roleId\": " + role("인사 담당")).andExpect(status().isCreated());
    }

    @Test
    void 프로필_사진은_내가_올린_아직_안_쓴_파일만() throws Exception {
        // 남이 올린 파일
        long kimsPhoto = upload(kim, "PROFILE");
        register(hr, "other", "\"profileFileId\": " + kimsPhoto)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.profileFileId").exists());
        // 내가 올렸어도 이미 다른 직원의 프로필 사진이면 거부 — 등록 · 관리자 수정 모두
        long hrPhoto = upload(hr, "PROFILE");
        register(hr, "first", "\"profileFileId\": " + hrPhoto).andExpect(status().isCreated());
        register(hr, "second", "\"profileFileId\": " + hrPhoto)
                .andExpect(jsonPath("$.error.fields.profileFileId").value("이미 다른 곳에 쓰인 파일입니다"));
        json(hr, patch("/api/employees/" + kim.id()), "{\"profileFileId\": " + hrPhoto + "}")
                .andExpect(jsonPath("$.error.fields.profileFileId").exists());
        // 본인 수정도 같은 규칙 — 내가 올린 파일이라도 로고로 쓰였으면 거부
        long logo = upload(admin, "LOGO");
        json(admin, patch("/api/company"), "{\"logoFileId\": " + logo + "}").andExpect(status().isOk());
        json(admin, patch("/api/me/profile"), "{\"profileFileId\": " + logo + "}")
                .andExpect(jsonPath("$.error.fields.profileFileId").exists());
        json(kim, patch("/api/me/profile"), "{\"profileFileId\": " + kimsPhoto + "}").andExpect(status().isOk());
        // 이미 걸린 같은 파일을 다시 보내는 것은 된다(바뀌지 않음)
        json(kim, patch("/api/me/profile"), "{\"profileFileId\": " + kimsPhoto + "}").andExpect(status().isOk());
    }

    @Test
    void 로고도_내가_올린_아직_안_쓴_파일만() throws Exception {
        long kimsPhoto = upload(kim, "PROFILE");
        json(kim, patch("/api/me/profile"), "{\"profileFileId\": " + kimsPhoto + "}").andExpect(status().isOk());
        json(admin, patch("/api/company"), "{\"logoFileId\": " + kimsPhoto + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.logoFileId").value("파일을 다시 올려 주세요"));     // 남이 올린 파일
        long adminPhoto = upload(admin, "PROFILE");
        json(admin, patch("/api/me/profile"), "{\"profileFileId\": " + adminPhoto + "}").andExpect(status().isOk());
        json(admin, patch("/api/company"), "{\"logoFileId\": " + adminPhoto + "}")
                .andExpect(jsonPath("$.error.fields.logoFileId").value("이미 다른 곳에 쓰인 파일입니다"));
    }
}
