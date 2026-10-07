package com.nexuslabs.hr.global;

import com.nexuslabs.hr.support.TestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * JPA 조회를 MockMvc 로 확인하는 테스트의 견본(백엔드 안내 7장).
 * ① 클래스에 @Transactional 을 붙이지 않는다 ② TestFixture 로 새 회사를 만든다 ③ 지우지 않는다.
 * 회사가 매번 새로 생기고 모든 조회가 회사 단위라 남은 데이터가 다른 테스트에 영향을 주지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JpaWithFixtureTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixture fixture;

    @Test
    void 새로_만든_회사의_JPA_조회는_그_회사_행만_보인다() throws Exception {
        TestFixture.Company a = fixture.company("JPA견본A");
        fixture.orgUnit(a.id(), a.rootOrgUnitId(), "개발팀");
        TestFixture.Company b = fixture.company("JPA견본B");

        mvc.perform(get("/api/test/org-units").header("Authorization", fixture.token(a.adminId(), a.id())))
                .andExpect(jsonPath("$.data").value(contains("JPA견본A", "개발팀")));
        mvc.perform(get("/api/test/org-units").header("Authorization", fixture.token(b.adminId(), b.id())))
                .andExpect(jsonPath("$.data").value(contains("JPA견본B")));
    }
}
