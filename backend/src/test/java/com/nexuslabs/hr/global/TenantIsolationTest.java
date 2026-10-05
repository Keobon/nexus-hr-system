package com.nexuslabs.hr.global;

import com.nexuslabs.hr.global.auth.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * @TenantId 자동 필터 확인. 일부러 @Transactional 을 붙이지 않는다 —
 * 테스트 트랜잭션은 요청(JWT 필터)보다 먼저 Hibernate 세션을 열어서 회사가 정해지기 전 값(0)으로 고정되기 때문이다.
 * JPA 조회를 MockMvc로 검증하는 테스트는 이 클래스처럼 데이터를 넣고 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/fixture/common-base-cleanup.sql", "/fixture/common-base.sql"})
@Sql(scripts = "/fixture/common-base-cleanup.sql", executionPhase = AFTER_TEST_METHOD)
class TenantIsolationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void JPA_조회는_내_회사_행만_보인다() throws Exception {
        mvc.perform(get("/api/test/org-units").header("Authorization", "Bearer " + jwtProvider.issue(91104, 9001)))
                .andExpect(jsonPath("$.data").value(contains("회사A", "본부", "팀", "파트", "별도 조직")));
        mvc.perform(get("/api/test/org-units").header("Authorization", "Bearer " + jwtProvider.issue(92101, 9002)))
                .andExpect(jsonPath("$.data").value(contains("B 조직")));
    }
}
