package com.nexuslabs.hr.global;

import com.nexuslabs.hr.global.auth.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공통 기반(B-01) 통합 테스트. 테스트 DB(nexus_hr_test)에 데이터를 넣고 테스트마다 롤백한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/fixture/common-base.sql")
class CommonBaseIntegrationTest {

    private static final long A = 9001, B = 9002;
    private static final long E1 = 91101, E2 = 91102, E3 = 91103, E4 = 91104, E5 = 91105, E6 = 91106, E7 = 91107;
    private static final long B1 = 92101;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T as(T request, long employeeId, long companyId) {
        return request.header("Authorization", "Bearer " + jwtProvider.issue(employeeId, companyId));
    }

    // ---- 인증 ----

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void 위조_토큰은_401() throws Exception {
        mvc.perform(get("/api/test/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void 유효한_토큰이면_로그인_사용자가_정해진다() throws Exception {
        mvc.perform(as(get("/api/test/me"), E4, A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeId").value(E4))
                .andExpect(jsonPath("$.data.companyId").value(A))
                .andExpect(jsonPath("$.data.roleId").value(91202));
    }

    @Test
    void 비활성_계정과_퇴직자는_401() throws Exception {
        mvc.perform(as(get("/api/test/me"), E6, A))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_INACTIVE"));
        mvc.perform(as(get("/api/test/me"), E7, A))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_INACTIVE"));
    }

    @Test
    void 토큰의_회사와_직원_회사가_다르면_401() throws Exception {
        mvc.perform(as(get("/api/test/me"), B1, A))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_INACTIVE"));
    }

    @Test
    void 비밀번호_변경이_필요하면_허용된_API만_부를_수_있다() throws Exception {
        mvc.perform(as(get("/api/test/me"), E5, A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
        // 허용 목록(GET /api/me)은 필터를 통과한다 — 아직 컨트롤러가 없으므로 404
        mvc.perform(as(get("/api/me"), E5, A))
                .andExpect(status().isNotFound());
    }

    @Test
    void 공개_엔드포인트는_토큰_없이_통과한다() throws Exception {
        // 컨트롤러는 B-02에서 만든다. 필터를 통과하면 401이 아닌 404
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ---- 권한 · 범위 ----

    @Test
    void 권한_코드가_없으면_403() throws Exception {
        mvc.perform(as(get("/api/test/payroll"), E4, A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 전사_범위() throws Exception {
        mvc.perform(as(get("/api/test/employees"), E4, A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.all").value(true));
    }

    @Test
    void 팀_범위는_내가_조직장인_조직과_하위_조직_전체() throws Exception {
        mvc.perform(as(get("/api/test/employees"), E1, A))
                .andExpect(jsonPath("$.data.all").value(false))
                .andExpect(jsonPath("$.data.ids").value(contains((int) E1, (int) E2, (int) E3, (int) E7)));
        mvc.perform(as(get("/api/test/employees"), E2, A))
                .andExpect(jsonPath("$.data.ids").value(contains((int) E2, (int) E3, (int) E7)));
    }

    @Test
    void 조직장이_아니면_팀_범위는_0명() throws Exception {
        mvc.perform(as(get("/api/test/employees"), E3, A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ids").value(empty()));
    }

    @Test
    void 단건은_범위_밖이면_OUT_OF_SCOPE() throws Exception {
        mvc.perform(as(get("/api/test/employees/" + E3), E2, A))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/test/employees/" + E1), E2, A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
    }

    @Test
    void 비활성_조직의_조직장은_팀_범위가_없다() throws Exception {
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = 91002");
        mvc.perform(as(get("/api/test/employees"), E2, A))
                .andExpect(jsonPath("$.data.ids").value(empty()));
    }

    @Test
    void 역할_권한을_바꾸면_다음_요청부터_반영된다() throws Exception {
        jdbc.update("INSERT INTO role_permission (company_id, role_id, permission_code, scope) VALUES (?, 91202, 'PAYROLL_READ', 'ALL')", A);
        mvc.perform(as(get("/api/test/payroll"), E4, A))
                .andExpect(status().isOk());
    }

    // ---- 에러 응답 ----

    @Test
    void BusinessException은_코드_메시지_details로_응답한다() throws Exception {
        mvc.perform(as(get("/api/test/business-error"), E4, A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAVE_INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.error.message").value("잔여 일수가 부족합니다"))
                .andExpect(jsonPath("$.error.details.remaining").value(1));
    }

    @Test
    void 검증_실패는_필드별_메시지() throws Exception {
        mvc.perform(as(post("/api/test/validate"), E4, A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").value("이름을 입력하세요"));
    }

    @Test
    void 잘못된_ENUM은_허용값을_알려준다() throws Exception {
        mvc.perform(as(post("/api/test/validate"), E4, A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\",\"code\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.error.details.allowed[0]").value("COMPANY_MANAGE"));
    }

    // ---- 감사 로그 ----

    @Test
    void 감사_로그는_행위자와_회사를_기록한다() throws Exception {
        mvc.perform(as(post("/api/test/audit"), E4, A)).andExpect(status().isOk());
        var row = jdbc.queryForMap(
                "SELECT company_id, actor_id, action::text AS action, after_value->>'name' AS name FROM audit_log WHERE target_id = 91103");
        assertThat(row).containsEntry("company_id", A).containsEntry("actor_id", E4)
                .containsEntry("action", "UPDATE").containsEntry("name", "후");
    }

    // ---- 파일 ----

    @Test
    void 파일을_올리고_올린_사람이_내려받는다() throws Exception {
        String body = mvc.perform(as(multipart("/api/files")
                                .file(new MockMultipartFile("file", "영수증.pdf", "application/pdf", PDF)), E3, A)
                        .param("purpose", "RECEIPT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.originalName").value("영수증.pdf"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(as(get("/api/files/" + id), E3, A))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", startsWith("inline;")));
        // 연결 안 된 파일은 올린 사람만
        mvc.perform(as(get("/api/files/" + id), E4, A))
                .andExpect(status().isNotFound());
        // 다른 회사는 존재 여부도 모른다
        mvc.perform(as(get("/api/files/" + id), B1, B))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로고로_연결된_파일은_같은_회사_누구나_본다() throws Exception {
        String body = mvc.perform(as(multipart("/api/files")
                                .file(new MockMultipartFile("file", "logo.png", "image/png", PNG)), E4, A)
                        .param("purpose", "LOGO"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
        jdbc.update("UPDATE company SET logo_file_id = ? WHERE id = ?", id, A);

        mvc.perform(as(get("/api/files/" + id), E3, A)).andExpect(status().isOk());
        mvc.perform(as(get("/api/files/" + id), B1, B)).andExpect(status().isNotFound());
    }

    @Test
    void 파일_형식은_내용으로_판단한다() throws Exception {
        // 확장자·Content-Type이 png여도 내용이 아니면 거부
        mvc.perform(as(multipart("/api/files")
                                .file(new MockMultipartFile("file", "a.png", "image/png", "hello".getBytes())), E4, A)
                        .param("purpose", "RECEIPT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"));
        // 로고는 이미지만
        mvc.perform(as(multipart("/api/files")
                                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF)), E4, A)
                        .param("purpose", "LOGO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void 용도별_업로드_권한() throws Exception {
        mvc.perform(as(multipart("/api/files")
                                .file(new MockMultipartFile("file", "logo.png", "image/png", PNG)), E3, A)
                        .param("purpose", "LOGO"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
