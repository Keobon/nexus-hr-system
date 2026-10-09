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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-EMP-03 본인 정보 수정 (API 설계서 6장 PATCH /me/profile) — 전화 · 주소 · 영문명 · 비상연락처 · 프로필 사진만.
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyProfileUpdateTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    private static final byte[] PDF = "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;

    TestFixture.Company company;
    TestFixture.Employee kim;
    TestFixture.Employee lee;

    @BeforeEach
    void setUp() {
        company = fixture.company("본인수정테스트");
        kim = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
        lee = fixture.employee(company.id(), company.rootOrgUnitId(), "직원", false);
    }

    private ResultActions update(TestFixture.Employee e, String body) throws Exception {
        return mvc.perform(patch("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", fixture.token(e.id(), company.id())));
    }

    private long upload(TestFixture.Employee e, String purpose, String name, byte[] content) throws Exception {
        String body = mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", name, null, content))
                        .param("purpose", purpose).header("Authorization", fixture.token(e.id(), company.id())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private Map<String, Object> row(TestFixture.Employee e) {
        return jdbc.queryForMap("""
                SELECT name, phone, address, name_en, emergency_name, emergency_relation, emergency_phone,
                       profile_file_id FROM employee WHERE id = ?""", e.id());
    }

    @Test
    void 보낸_항목만_바꾸고_null_은_비운다() throws Exception {
        update(kim, """
                {"phone": " 010-1234-5678 ", "address": "서울시 강남구", "nameEn": "Kim",
                 "emergencyName": "김부모", "emergencyRelation": "부", "emergencyPhone": "010-0000-0000"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(kim.id()))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.emergencyName").value("김부모"))
                .andExpect(jsonPath("$.data.leaveBalances").isArray());            // GET /me/profile 과 같은 모양

        update(kim, "{\"address\": null, \"nameEn\": \"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value(nullValue()))
                .andExpect(jsonPath("$.data.nameEn").value(nullValue()))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"));      // 안 보낸 항목은 그대로

        assertThat(row(kim)).containsEntry("phone", "010-1234-5678").containsEntry("address", null)
                .containsEntry("emergency_relation", "부");
        assertThat(row(lee)).containsEntry("phone", null);                         // 남의 정보는 그대로
    }

    @Test
    void 관리자만_고치는_항목은_FORBIDDEN_모르는_항목과_길이_초과는_VALIDATION_ERROR() throws Exception {
        String name = (String) row(kim).get("name");
        update(kim, "{\"phone\": \"010-1111-2222\", \"name\": \"개명\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        update(kim, "{\"hrMemo\": \"좋은 직원\"}").andExpect(status().isForbidden());
        update(kim, "{\"orgUnitId\": 1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.orgUnitId").exists());
        update(kim, "{\"phone\": \"" + "1".repeat(21) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.phone").exists());
        update(kim, "{\"phone\": {\"a\": 1}}").andExpect(status().isBadRequest());

        assertThat(row(kim)).containsEntry("name", name).containsEntry("phone", null);   // 아무것도 안 바뀜
    }

    @Test
    void 프로필_사진은_본인이_올린_이미지만_걸고_비울_수_있다() throws Exception {
        long mine = upload(kim, "PROFILE", "나.png", PNG);
        long others = upload(lee, "PROFILE", "남.png", PNG);
        long pdf = upload(kim, "RECEIPT", "영수증.pdf", PDF);

        update(kim, "{\"profileFileId\": " + others + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.profileFileId").exists());
        update(kim, "{\"profileFileId\": 999999999}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.profileFileId").exists());
        update(kim, "{\"profileFileId\": " + pdf + "}")
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"));

        update(kim, "{\"profileFileId\": " + mine + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileFileId").value(mine));
        update(kim, "{\"phone\": \"010-1\"}")                                       // 사진은 그대로
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileFileId").value(mine));
        update(kim, "{\"profileFileId\": null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileFileId").value(nullValue()));
    }
}
