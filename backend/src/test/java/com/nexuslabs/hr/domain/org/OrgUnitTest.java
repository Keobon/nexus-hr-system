package com.nexuslabs.hr.domain.org;

import com.nexuslabs.hr.domain.org.service.OrgUnitService;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-ORG-01 조직 관리 · F-ORG-02 조직도 (API 설계서 5장).
 * JPA 를 쓰는 기능이라 @Transactional 없이 새 회사를 만들고 지우지 않는다(백엔드 안내 7장).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrgUnitTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestFixture fixture;
    @Autowired OrgUnitService orgUnitService;

    TestFixture.Company company;
    long root;

    @BeforeEach
    void setUp() {
        company = fixture.company("조직테스트");
        root = company.rootOrgUnitId();
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.token(company.adminId(), company.id())));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long org(long parentId, String name) {
        return fixture.orgUnit(company.id(), parentId, name);
    }

    private long member(long orgUnitId) {
        return fixture.employee(company.id(), orgUnitId, "직원", false).id();
    }

    private void resign(long employeeId) {
        jdbc.update("UPDATE employee SET status = 'RESIGNED' WHERE id = ?", employeeId);
    }

    private Long leadOf(long orgUnitId) {
        return jdbc.queryForObject("SELECT lead_employee_id FROM org_unit WHERE id = ?", Long.class, orgUnitId);
    }

    private boolean exists(long orgUnitId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM org_unit WHERE id = ?)", Boolean.class, orgUnitId);
    }

    // ---------------------------------------------------------------- 등록

    @Test
    void 등록하면_같은_상위_아래_맨_뒤에_놓인다() throws Exception {
        asAdmin(json(post("/api/org-units"), """
                {"parentId": %d, "name": " 개발본부 ", "levelName": "본부", "monthlyBudget": 50000000}
                """.formatted(root)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentId").value(root))
                .andExpect(jsonPath("$.data.name").value("개발본부"))
                .andExpect(jsonPath("$.data.levelName").value("본부"))
                .andExpect(jsonPath("$.data.monthlyBudget").value(50000000))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.lead").value(nullValue()));
        asAdmin(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \"경영지원본부\"}".formatted(root)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sortOrder").value(2))
                .andExpect(jsonPath("$.data.levelName").value(nullValue()));
    }

    @Test
    void 등록_거부_같은_이름_비활성_상위_없는_상위_빈_이름() throws Exception {
        long dev = org(root, "개발본부");
        long closed = org(root, "폐지본부");
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = ?", closed);

        asAdmin(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \"개발본부\"}".formatted(root)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        // 상위가 다르면 같은 이름을 쓸 수 있다
        asAdmin(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \"개발본부\"}".formatted(dev)))
                .andExpect(status().isCreated());
        asAdmin(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \"신설팀\"}".formatted(closed)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        asAdmin(json(post("/api/org-units"), "{\"parentId\": 999999999, \"name\": \"신설팀\"}"))
                .andExpect(status().isNotFound());
        asAdmin(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \" \"}".formatted(root)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        asAdmin(json(post("/api/org-units"), "{\"name\": \"상위없음\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.parentId").exists());
    }

    // ---------------------------------------------------------------- 수정

    @Test
    void 수정은_보낸_필드만_바꾸고_null_은_비운다() throws Exception {
        long team = org(root, "백엔드팀");
        asAdmin(json(patch("/api/org-units/" + team), """
                {"name": "서버팀", "levelName": "팀", "monthlyBudget": 1000, "sortOrder": 7}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("서버팀"))
                .andExpect(jsonPath("$.data.levelName").value("팀"))
                .andExpect(jsonPath("$.data.monthlyBudget").value(1000))
                .andExpect(jsonPath("$.data.sortOrder").value(7));

        // 이름만 보내면 나머지는 그대로
        asAdmin(json(patch("/api/org-units/" + team), "{\"name\": \"플랫폼팀\"}"))
                .andExpect(jsonPath("$.data.name").value("플랫폼팀"))
                .andExpect(jsonPath("$.data.levelName").value("팀"))
                .andExpect(jsonPath("$.data.monthlyBudget").value(1000))
                .andExpect(jsonPath("$.data.sortOrder").value(7));

        // null 을 보내면 비운다
        asAdmin(json(patch("/api/org-units/" + team), "{\"levelName\": null, \"monthlyBudget\": null}"))
                .andExpect(jsonPath("$.data.name").value("플랫폼팀"))
                .andExpect(jsonPath("$.data.levelName").value(nullValue()))
                .andExpect(jsonPath("$.data.monthlyBudget").value(nullValue()));
    }

    @Test
    void 수정_거부_같은_이름_잘못된_값() throws Exception {
        org(root, "인사팀");
        long finance = org(root, "재무팀");

        asAdmin(json(patch("/api/org-units/" + finance), "{\"name\": \"인사팀\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        asAdmin(json(patch("/api/org-units/" + finance), "{\"name\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        asAdmin(json(patch("/api/org-units/" + finance), "{\"monthlyBudget\": -1}"))
                .andExpect(status().isBadRequest());
        asAdmin(json(patch("/api/org-units/" + finance), "{\"sortOrder\": \"첫째\"}"))
                .andExpect(status().isBadRequest());
        // 최상위 조직도 이름은 바꿀 수 있다
        asAdmin(json(patch("/api/org-units/" + root), "{\"name\": \"새회사명\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(nullValue()));
    }

    // ---------------------------------------------------------------- 조직장

    @Test
    void 조직장은_그_조직_소속_직원만_지정하고_null_이면_해제한다() throws Exception {
        long team = org(root, "백엔드팀");
        long lead = member(team);

        asAdmin(json(patch("/api/org-units/" + team), "{\"leadEmployeeId\": %d}".formatted(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lead.id").value(lead))
                .andExpect(jsonPath("$.data.lead.name").exists());

        // leadEmployeeId 를 안 보내면 조직장은 그대로다
        asAdmin(json(patch("/api/org-units/" + team), "{\"levelName\": \"팀\"}"))
                .andExpect(jsonPath("$.data.lead.id").value(lead));
        assertThat(leadOf(team)).isEqualTo(lead);

        asAdmin(json(patch("/api/org-units/" + team), "{\"leadEmployeeId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lead").value(nullValue()));
        assertThat(leadOf(team)).isNull();
    }

    @Test
    void 조직장_지정_거부_다른_조직_퇴직자_다른_회사() throws Exception {
        long team = org(root, "백엔드팀");
        long outsider = member(root);
        long resigned = member(team);
        resign(resigned);
        TestFixture.Company other = fixture.company("조직테스트타사");

        asAdmin(json(patch("/api/org-units/" + team), "{\"leadEmployeeId\": %d}".formatted(outsider)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORG_LEAD_NOT_MEMBER"));
        asAdmin(json(patch("/api/org-units/" + team), "{\"leadEmployeeId\": %d}".formatted(resigned)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPLOYEE_RESIGNED"));
        asAdmin(json(patch("/api/org-units/" + team), "{\"leadEmployeeId\": %d}".formatted(other.adminId())))
                .andExpect(status().isNotFound());
        assertThat(leadOf(team)).isNull();
    }

    @Test
    void 휴직자는_조직장으로_지정할_수_있다() throws Exception {
        long team = org(root, "백엔드팀");
        long onLeave = member(team);
        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", onLeave);

        asAdmin(json(patch("/api/org-units/" + team), "{\"leadEmployeeId\": %d}".formatted(onLeave)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lead.id").value(onLeave));
    }

    // ---------------------------------------------------------------- 이동

    @Test
    void 이동하면_상위_조직이_바뀐다() throws Exception {
        long dev = org(root, "개발본부");
        long biz = org(root, "사업본부");
        long team = org(dev, "데이터팀");

        asAdmin(json(post("/api/org-units/" + team + "/move"), "{\"parentId\": %d}".formatted(biz)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(biz));

        asAdmin(get("/api/org-units/tree"))
                .andExpect(jsonPath("$.data.children[?(@.name == '사업본부')].children[0].name").value(contains("데이터팀")))
                .andExpect(jsonPath("$.data.children[?(@.name == '개발본부')].children.length()").value(contains(0)));
    }

    @Test
    void 이동_거부_자기_자신_하위_조직_최상위_같은_이름_비활성_상위() throws Exception {
        long dev = org(root, "개발본부");
        long team = org(dev, "백엔드팀");
        long part = org(team, "결제파트");
        long sameName = org(root, "백엔드팀");
        long closed = org(root, "폐지본부");
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = ?", closed);

        asAdmin(json(post("/api/org-units/" + dev + "/move"), "{\"parentId\": %d}".formatted(dev)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORG_CYCLE"));
        asAdmin(json(post("/api/org-units/" + dev + "/move"), "{\"parentId\": %d}".formatted(part)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORG_CYCLE"));
        asAdmin(json(post("/api/org-units/" + root + "/move"), "{\"parentId\": %d}".formatted(dev)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_ROOT_LOCKED"));
        // 최상위 조직 아래에 이미 "백엔드팀"이 있다
        asAdmin(json(post("/api/org-units/" + team + "/move"), "{\"parentId\": %d}".formatted(root)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
        asAdmin(json(post("/api/org-units/" + team + "/move"), "{\"parentId\": %d}".formatted(closed)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        assertThat(sameName).isNotEqualTo(team);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM org_unit WHERE id = ?", Long.class, team)).isEqualTo(dev);
    }

    // ---------------------------------------------------------------- 비활성화

    @Test
    void 비활성화는_재직_직원이나_활성_하위_조직이_있으면_거부한다() throws Exception {
        long dev = org(root, "개발본부");
        long team = org(dev, "백엔드팀");
        long employee = member(team);

        asAdmin(post("/api/org-units/" + dev + "/deactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_HAS_MEMBERS"));
        asAdmin(post("/api/org-units/" + team + "/deactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_HAS_MEMBERS"));
        asAdmin(post("/api/org-units/" + root + "/deactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_ROOT_LOCKED"));

        // 퇴직자만 남은 조직은 비활성화할 수 있다
        resign(employee);
        asAdmin(post("/api/org-units/" + team + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
        asAdmin(post("/api/org-units/" + dev + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void 비활성_조직은_조직도에서_숨기고_ORG_MANAGE_만_함께_볼_수_있다() throws Exception {
        long team = org(root, "임시팀");
        org(root, "상시팀");
        TestFixture.Employee staff = fixture.employee(company.id(), root, "직원", false);
        String staffToken = fixture.token(staff.id(), company.id());
        asAdmin(post("/api/org-units/" + team + "/deactivate")).andExpect(status().isOk());

        asAdmin(get("/api/org-units/tree"))
                .andExpect(jsonPath("$.data.children[*].name").value(contains("상시팀")));
        asAdmin(get("/api/org-units/tree?includeInactive=true"))
                .andExpect(jsonPath("$.data.children[*].name").value(contains("임시팀", "상시팀")))
                .andExpect(jsonPath("$.data.children[0].isActive").value(false));
        // 권한이 없으면 includeInactive 를 보내도 숨긴다
        mvc.perform(get("/api/org-units/tree?includeInactive=true").header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children[*].name").value(contains("상시팀")));
    }

    @Test
    void 다시_활성화하려면_상위_조직이_활성이어야_한다() throws Exception {
        long dev = org(root, "개발본부");
        long team = org(dev, "백엔드팀");
        asAdmin(post("/api/org-units/" + team + "/deactivate")).andExpect(status().isOk());
        asAdmin(post("/api/org-units/" + dev + "/deactivate")).andExpect(status().isOk());

        asAdmin(post("/api/org-units/" + team + "/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INACTIVE_REFERENCE"));
        asAdmin(post("/api/org-units/" + dev + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
        asAdmin(post("/api/org-units/" + team + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    // ---------------------------------------------------------------- 삭제

    @Test
    void 쓰인_적_없는_조직은_실제로_삭제된다() throws Exception {
        long team = org(root, "신설팀");

        asAdmin(delete("/api/org-units/" + team))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DELETED"));
        assertThat(exists(team)).isFalse();
    }

    @Test
    void 삭제_거부_재직_직원_활성_하위_조직_최상위() throws Exception {
        long dev = org(root, "개발본부");
        long team = org(dev, "백엔드팀");
        member(team);

        asAdmin(delete("/api/org-units/" + team))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_HAS_MEMBERS"));
        asAdmin(delete("/api/org-units/" + dev))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_HAS_MEMBERS"));
        asAdmin(delete("/api/org-units/" + root))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORG_ROOT_LOCKED"));
        assertThat(exists(team)).isTrue();
    }

    @Test
    void 퇴직자나_비활성_하위_조직만_남은_조직은_삭제_대신_비활성화된다() throws Exception {
        long withResigned = org(root, "해체팀");
        resign(member(withResigned));
        long withInactiveChild = org(root, "해체본부");
        long child = org(withInactiveChild, "해체파트");
        jdbc.update("UPDATE org_unit SET is_active = FALSE WHERE id = ?", child);

        asAdmin(delete("/api/org-units/" + withResigned)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        asAdmin(delete("/api/org-units/" + withInactiveChild)).andExpect(jsonPath("$.data.result").value("DEACTIVATED"));

        assertThat(jdbc.queryForObject("SELECT is_active FROM org_unit WHERE id = ?", Boolean.class, withResigned))
                .isFalse();
        assertThat(exists(withInactiveChild)).isTrue();
    }

    @Test
    void 발령_이력이_있는_조직은_삭제_대신_비활성화된다() throws Exception {
        long from = org(root, "옛팀");
        long gradeId = jdbc.queryForObject(
                "INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, '사원', 1) RETURNING id",
                Long.class, company.id());
        jdbc.update("""
                INSERT INTO assignment_history (company_id, employee_id, assignment_type, from_org_unit_id, to_org_unit_id,
                                                to_job_grade_id, reason, created_by)
                VALUES (?, ?, 'TRANSFER', ?, ?, ?, '조직 개편', ?)
                """, company.id(), company.adminId(), from, root, gradeId, company.adminId());

        asAdmin(delete("/api/org-units/" + from))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        assertThat(exists(from)).isTrue();
    }

    @Test
    void 급여명세서의_정산_당시_소속으로_남은_조직은_삭제_대신_비활성화된다() throws Exception {
        long settled = org(root, "정산팀");
        long runId = jdbc.queryForObject("""
                INSERT INTO payroll_run (company_id, pay_month, pay_date, company_name_snap, ceo_name_snap,
                                         business_reg_no_snap, company_address_snap, confirmed_by)
                VALUES (?, '2026-09', DATE '2026-09-25', '조직테스트', '대표', '000-00-00000', '서울', ?) RETURNING id
                """, Long.class, company.id(), company.adminId());
        jdbc.update("""
                INSERT INTO paystub (company_id, payroll_run_id, employee_id, employee_no_snap, employee_name_snap,
                                     org_unit_id_snap, org_name_snap, base_pay, ordinary_hourly_wage, dependents_count,
                                     children_count, worked_days, month_days, gross_pay, taxable_pay, income_tax,
                                     local_income_tax, total_deduction, net_pay, company_burden_total)
                VALUES (?, ?, ?, 'T-1', '관리자', ?, '정산팀', 3000000, 14354, 1, 0, 30, 30, 3000000, 3000000, 0, 0, 0,
                        3000000, 0)
                """, company.id(), runId, company.adminId(), settled);

        asAdmin(delete("/api/org-units/" + settled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DEACTIVATED"));
        assertThat(exists(settled)).isTrue();
    }

    // ---------------------------------------------------------------- 조직도 · 소속 직원

    @Test
    void 조직도는_정렬_순서대로_조직장과_인원을_담는다() throws Exception {
        long dev = org(root, "개발본부");
        long biz = org(root, "사업본부");
        jdbc.update("UPDATE org_unit SET sort_order = 2, level_name = '본부' WHERE id = ?", dev);
        jdbc.update("UPDATE org_unit SET sort_order = 1 WHERE id = ?", biz);
        long team = org(dev, "백엔드팀");
        long devLead = member(dev);
        fixture.lead(company.id(), dev, devLead);
        member(team);
        member(team);
        resign(member(team));

        asAdmin(get("/api/org-units/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(root))
                .andExpect(jsonPath("$.data.name").value("조직테스트"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.lead").value(nullValue()))
                .andExpect(jsonPath("$.data.directCount").value(1))   // 회사 등록 때 만든 관리자
                .andExpect(jsonPath("$.data.totalCount").value(4))    // 퇴직자 1명은 세지 않는다
                .andExpect(jsonPath("$.data.children[*].name").value(contains("사업본부", "개발본부")))
                .andExpect(jsonPath("$.data.children[1].levelName").value("본부"))
                .andExpect(jsonPath("$.data.children[1].lead.id").value(devLead))
                .andExpect(jsonPath("$.data.children[1].directCount").value(1))
                .andExpect(jsonPath("$.data.children[1].totalCount").value(3))
                .andExpect(jsonPath("$.data.children[1].sortOrder").value(2))
                .andExpect(jsonPath("$.data.children[1].children[0].name").value("백엔드팀"))
                .andExpect(jsonPath("$.data.children[1].children[0].totalCount").value(2))
                .andExpect(jsonPath("$.data.children[1].children[0].children.length()").value(0));
    }

    @Test
    void 조직도의_예산은_ORG_MANAGE_가_있을_때만_내려간다() throws Exception {
        long dev = org(root, "개발본부");
        org(root, "사업본부");
        jdbc.update("UPDATE org_unit SET monthly_budget = 70000000, sort_order = -1 WHERE id = ?", dev);
        TestFixture.Employee staff = fixture.employee(company.id(), root, "직원", false);

        // 권한이 있으면 예산이 없는 조직도 null 로 내려간다
        asAdmin(get("/api/org-units/tree"))
                .andExpect(jsonPath("$.data.children[0].monthlyBudget").value(70000000))
                .andExpect(jsonPath("$.data.children[1].monthlyBudget").value(nullValue()))
                .andExpect(jsonPath("$.data.children[1].monthlyBudget").hasJsonPath());
        mvc.perform(get("/api/org-units/tree").header("Authorization", fixture.token(staff.id(), company.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children[0].name").value("개발본부"))
                .andExpect(jsonPath("$.data.children[0].sortOrder").value(-1))
                .andExpect(jsonPath("$.data.children[0].monthlyBudget").doesNotHaveJsonPath());
    }

    @Test
    void 소속_직원은_직속만_또는_하위_조직까지_본다() throws Exception {
        long dev = org(root, "개발본부");
        long team = org(dev, "백엔드팀");
        long gradeId = jdbc.queryForObject(
                "INSERT INTO job_grade (company_id, name, sort_order) VALUES (?, '과장', 1) RETURNING id",
                Long.class, company.id());
        long titleId = jdbc.queryForObject(
                "INSERT INTO job_title (company_id, name, sort_order) VALUES (?, '본부장', 1) RETURNING id",
                Long.class, company.id());
        long head = member(dev);
        jdbc.update("UPDATE employee SET job_grade_id = ?, job_title_id = ? WHERE id = ?", gradeId, titleId, head);
        long developer = member(team);
        resign(member(team));

        asAdmin(get("/api/org-units/" + dev + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(head))
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].jobGradeName").value("과장"))
                .andExpect(jsonPath("$.data[0].jobTitleName").value("본부장"))
                .andExpect(jsonPath("$.data[0].profileFileId").value(nullValue()));
        // 직급이 있는 사람이 먼저, 퇴직자는 빠진다
        asAdmin(get("/api/org-units/" + dev + "/members?includeSub=true"))
                .andExpect(jsonPath("$.data[*].id").value(contains((int) head, (int) developer)))
                .andExpect(jsonPath("$.data[1].jobGradeName").value(nullValue()));
    }

    // ---------------------------------------------------------------- 조직장 자동 해제(BR-ORG-003)

    @Test
    void 조직장이_조직을_떠나거나_퇴직하면_해제되고_휴직이면_그대로다() {
        long teamA = org(root, "A팀");
        long teamB = org(root, "B팀");
        long teamC = org(root, "C팀");
        long moved = member(teamA);
        long resigned = member(teamB);
        long onLeave = member(teamC);
        fixture.lead(company.id(), teamA, moved);
        fixture.lead(company.id(), teamB, resigned);
        fixture.lead(company.id(), teamC, onLeave);

        // 아무 일도 없으면 그대로
        assertThat(orgUnitService.releaseLeadsIfLeft(company.id(), moved)).isEmpty();
        assertThat(leadOf(teamA)).isEqualTo(moved);

        jdbc.update("UPDATE employee SET org_unit_id = ? WHERE id = ?", teamB, moved);
        assertThat(orgUnitService.releaseLeadsIfLeft(company.id(), moved)).containsExactly(teamA);
        assertThat(leadOf(teamA)).isNull();

        resign(resigned);
        assertThat(orgUnitService.releaseLeadsIfLeft(company.id(), resigned)).containsExactly(teamB);
        assertThat(leadOf(teamB)).isNull();

        jdbc.update("UPDATE employee SET status = 'ON_LEAVE' WHERE id = ?", onLeave);
        assertThat(orgUnitService.releaseLeadsIfLeft(company.id(), onLeave)).isEmpty();
        assertThat(leadOf(teamC)).isEqualTo(onLeave);
    }

    // ---------------------------------------------------------------- 권한 · 회사 격리

    @Test
    void 관리는_ORG_MANAGE_가_있어야_하고_조회는_로그인만_하면_된다() throws Exception {
        long team = org(root, "백엔드팀");
        TestFixture.Employee staff = fixture.employee(company.id(), team, "직원", false);
        String staffToken = fixture.token(staff.id(), company.id());

        mvc.perform(get("/api/org-units/tree").header("Authorization", staffToken)).andExpect(status().isOk());
        mvc.perform(get("/api/org-units/" + team + "/members").header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \"몰래팀\"}".formatted(root))
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(json(patch("/api/org-units/" + team), "{\"name\": \"내팀\"}").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/org-units/" + team + "/move"), "{\"parentId\": %d}".formatted(root))
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/org-units/" + team + "/deactivate").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/org-units/" + team).header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회사의_조직은_보이지_않고_모든_요청이_404() throws Exception {
        long team = org(root, "백엔드팀");
        member(team);
        TestFixture.Company other = fixture.company("조직테스트타사");
        String otherToken = fixture.token(other.adminId(), other.id());

        mvc.perform(get("/api/org-units/tree").header("Authorization", otherToken))
                .andExpect(jsonPath("$.data.id").value(other.rootOrgUnitId()))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.children.length()").value(0));
        mvc.perform(get("/api/org-units/" + team + "/members").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch("/api/org-units/" + team), "{\"name\": \"남의팀\"}").header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        // 다른 회사 조직을 상위로 지정할 수 없다
        mvc.perform(json(post("/api/org-units"), "{\"parentId\": %d, \"name\": \"침투팀\"}".formatted(team))
                        .header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/org-units/" + team + "/move"), "{\"parentId\": %d}".formatted(other.rootOrgUnitId()))
                        .header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/org-units/" + team + "/deactivate").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/org-units/" + team).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT name FROM org_unit WHERE id = ?", String.class, team)).isEqualTo("백엔드팀");
    }
}
