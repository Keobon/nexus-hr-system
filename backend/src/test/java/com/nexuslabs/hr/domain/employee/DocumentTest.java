package com.nexuslabs.hr.domain.employee;

import com.jayway.jsonpath.JsonPath;
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

import static com.nexuslabs.hr.support.TestClock.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-08 직원 서류 · F-COMP-07 회사 서류 · 서류 파일 내려받기 권한 (API 설계서 6장 · 3장 · 14장, BR-FILE-001).
 * 시계는 2030-03-04(월). 인사 담당은 EMPLOYEE_MANAGE 는 있고 COMPANY_MANAGE 는 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.class)
class DocumentTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired TestClock.MutableClock clock;

    TestFixture.Company company;
    TestFixture.Employee hr;
    TestFixture.Employee kim;
    TestFixture.Employee lee;

    @BeforeEach
    void setUp() {
        clock.set(MONDAY.atTime(9, 0));
        company = fixture.company("서류테스트");
        hr = fixture.employee(company.id(), company.rootOrgUnitId(), "인사 담당", false);
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        lee = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions as(long employeeId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(employeeId, company.id())));
    }

    private long upload(long employeeId, String purpose) throws Exception {
        String res = mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "서류.png", "image/png", PNG))
                        .param("purpose", purpose).header("Authorization", fixture.token(employeeId, company.id())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    private ResultActions employeeDoc(long employeeId, String body) throws Exception {
        return as(hr.id(), post("/api/employees/" + employeeId + "/documents")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions companyDoc(long actorId, String body) throws Exception {
        return as(actorId, post("/api/company/documents").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 직원_서류는_같은_종류를_올리면_새_버전이_현재본이_된다() throws Exception {
        long v1 = upload(hr.id(), "EMPLOYEE_DOCUMENT");
        employeeDoc(kim.id(), """
                {"docType": "LABOR_CONTRACT", "docName": "무시", "fileId": %d, "issuedDate": "2029-03-01",
                 "expiresAt": "2030-03-03", "memo": " 1년 계약 "}""".formatted(v1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.docType").value("LABOR_CONTRACT"))
                .andExpect(jsonPath("$.data.docName").value(nullValue()))
                .andExpect(jsonPath("$.data.current.versionNo").value(1))
                .andExpect(jsonPath("$.data.current.fileName").value("서류.png"))
                .andExpect(jsonPath("$.data.current.expired").value(true))          // 만료일 3/3 < 오늘 3/4
                .andExpect(jsonPath("$.data.current.memo").value("1년 계약"))
                .andExpect(jsonPath("$.data.current.uploadedById").value(hr.id()))
                .andExpect(jsonPath("$.data.versions", hasSize(0)));
        long v2 = upload(hr.id(), "EMPLOYEE_DOCUMENT");
        employeeDoc(kim.id(), "{\"docType\": \"LABOR_CONTRACT\", \"fileId\": %d, \"expiresAt\": \"2031-03-03\"}".formatted(v2))
                .andExpect(jsonPath("$.data.current.versionNo").value(2))
                .andExpect(jsonPath("$.data.current.fileId").value(v2))
                .andExpect(jsonPath("$.data.current.expired").value(false))
                .andExpect(jsonPath("$.data.versions[*].versionNo").value(contains(1)));

        // 기타는 이름마다 따로 버전을 센다
        employeeDoc(kim.id(), "{\"docType\": \"OTHER\", \"fileId\": %d}".formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.docName").exists());
        employeeDoc(kim.id(), "{\"docType\": \"OTHER\", \"docName\": \"포트폴리오\", \"fileId\": %d}"
                .formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT"))).andExpect(status().isCreated());
        employeeDoc(kim.id(), "{\"docType\": \"OTHER\", \"docName\": \"자격증 사본\", \"fileId\": %d}"
                .formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT"))).andExpect(status().isCreated());
        employeeDoc(kim.id(), "{\"docType\": \"OTHER\", \"docName\": \" 자격증 사본 \", \"fileId\": %d}"
                .formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT")))
                .andExpect(jsonPath("$.data.current.versionNo").value(2));

        as(hr.id(), get("/api/employees/" + kim.id() + "/documents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].docType").value(contains("LABOR_CONTRACT", "OTHER", "OTHER")))
                .andExpect(jsonPath("$.data[1].docName").value("자격증 사본"))
                .andExpect(jsonPath("$.data[2].docName").value("포트폴리오"));
        as(kim.id(), get("/api/me/documents")).andExpect(jsonPath("$.data", hasSize(3)));
        as(kim.id(), get("/api/employees/" + kim.id() + "/documents")).andExpect(status().isForbidden());
        // 직원 서류는 감사 로그 대상이 아니다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type LIKE '%DOCUMENT'", Long.class,
                company.id())).isZero();
    }

    @Test
    void 연결할_파일과_날짜를_확인하고_퇴직자는_조회만_된다() throws Exception {
        long used = upload(hr.id(), "EMPLOYEE_DOCUMENT");
        employeeDoc(kim.id(), "{\"docType\": \"RESUME\", \"fileId\": %d}".formatted(used)).andExpect(status().isCreated());
        employeeDoc(lee.id(), "{\"docType\": \"RESUME\", \"fileId\": %d}".formatted(used))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.fileId").value("이미 다른 곳에 쓰인 파일입니다"));
        long receipt = upload(kim.id(), "RECEIPT");                                  // 남이 올린 파일
        employeeDoc(kim.id(), "{\"docType\": \"RESUME\", \"fileId\": %d}".formatted(receipt))
                .andExpect(jsonPath("$.error.fields.fileId").value("파일을 다시 올려 주세요"));
        employeeDoc(kim.id(), "{\"docType\": \"RESUME\", \"fileId\": 999999999}")
                .andExpect(jsonPath("$.error.fields.fileId").exists());
        employeeDoc(kim.id(), "{\"docType\": \"RESUME\", \"fileId\": %d, \"issuedDate\": \"2030-03-02\", \"expiresAt\": \"2030-03-01\"}"
                .formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT")))
                .andExpect(jsonPath("$.error.fields.expiresAt").exists());
        employeeDoc(kim.id(), "{\"docType\": \"ID_CARD\", \"fileId\": 1}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));

        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", kim.id());
        employeeDoc(kim.id(), "{\"docType\": \"RESUME\", \"fileId\": %d}".formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
        as(hr.id(), get("/api/employees/" + kim.id() + "/documents")).andExpect(jsonPath("$.data", hasSize(1)));

        TestFixture.Company other = fixture.company("서류타사");
        TestFixture.Employee stranger = fixture.employee(other.id(), other.rootOrgUnitId(), "직원", false);
        as(hr.id(), get("/api/employees/" + stranger.id() + "/documents")).andExpect(status().isNotFound());
    }

    @Test
    void 직원_서류_파일은_관리자와_그_서류의_본인만_내려받는다() throws Exception {
        long v1 = upload(hr.id(), "EMPLOYEE_DOCUMENT");
        employeeDoc(kim.id(), "{\"docType\": \"BANKBOOK_COPY\", \"fileId\": %d}".formatted(v1));
        employeeDoc(kim.id(), "{\"docType\": \"BANKBOOK_COPY\", \"fileId\": %d}".formatted(upload(hr.id(), "EMPLOYEE_DOCUMENT")));

        as(hr.id(), get("/api/files/" + v1)).andExpect(status().isOk());             // 이전 버전도 같은 규칙
        as(kim.id(), get("/api/files/" + v1)).andExpect(status().isOk());
        as(lee.id(), get("/api/files/" + v1)).andExpect(status().isNotFound());
        as(company.adminId(), get("/api/files/" + v1)).andExpect(status().isOk());
    }

    @Test
    void 회사_서류는_COMPANY_MANAGE_만_올리고_보고_등록은_감사_로그를_남긴다() throws Exception {
        long admin = company.adminId();
        long v1 = upload(admin, "COMPANY_DOCUMENT");
        companyDoc(admin, "{\"docType\": \"BUSINESS_REGISTRATION\", \"fileId\": %d, \"issuedDate\": \"2020-01-02\"}".formatted(v1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.docType").value("BUSINESS_REGISTRATION"))
                .andExpect(jsonPath("$.data.current.versionNo").value(1))
                .andExpect(jsonPath("$.data.current.expired").value(false));
        companyDoc(admin, "{\"docType\": \"BUSINESS_REGISTRATION\", \"fileId\": %d}".formatted(upload(admin, "COMPANY_DOCUMENT")))
                .andExpect(jsonPath("$.data.current.versionNo").value(2))
                .andExpect(jsonPath("$.data.versions[0].fileId").value(v1));
        companyDoc(admin, "{\"docType\": \"OTHER\", \"fileId\": %d}".formatted(upload(admin, "COMPANY_DOCUMENT")))
                .andExpect(jsonPath("$.error.fields.docName").exists());

        as(admin, get("/api/company/documents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].versions", hasSize(1)));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE company_id = ? AND target_type = 'COMPANY_DOCUMENT' AND action = 'CREATE'",
                Long.class, company.id())).isEqualTo(2);

        as(hr.id(), get("/api/company/documents")).andExpect(status().isForbidden());
        companyDoc(hr.id(), "{\"docType\": \"OTHER\", \"docName\": \"x\", \"fileId\": 1}").andExpect(status().isForbidden());
        as(hr.id(), get("/api/files/" + v1)).andExpect(status().isNotFound());
        as(admin, get("/api/files/" + v1)).andExpect(status().isOk());
    }
}
