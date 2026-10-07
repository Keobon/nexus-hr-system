package com.nexuslabs.hr.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 백엔드 개발 안내 v2 6.3 "승인자" 예제와 같은 조직(넥서스랩스 축소판).
 * <pre>
 * 회사(박서준·대표이사) ┬ 개발본부(강하늘·본부장) ┬ 프론트엔드팀(서예린) ─ 조현우
 *                      │                         └ 백엔드팀(윤서연)
 *                      └ (관리자는 최상위 소속, 조직장 아님)
 * 휴가 기본 승인선 = [1 소속 조직장 → 2 한 단계 위 조직장], "본부장 휴가"(직책 본부장) = [1 직책 대표이사]
 * </pre>
 */
public final class DemoOrg {

    public final TestFixture.Company company;
    public final long devDivision, frontendTeam, backendTeam;
    public final long ceoTitle, divisionHeadTitle;
    public final TestFixture.Employee ceo, kang, seo, cho, yoon;

    public DemoOrg(TestFixture fixture, JdbcTemplate jdbc) {
        company = fixture.company("데모랩스");
        long cid = company.id();
        devDivision = fixture.orgUnit(cid, company.rootOrgUnitId(), "개발본부");
        frontendTeam = fixture.orgUnit(cid, devDivision, "프론트엔드팀");
        backendTeam = fixture.orgUnit(cid, devDivision, "백엔드팀");
        ceoTitle = title(jdbc, cid, "대표이사", 1);
        divisionHeadTitle = title(jdbc, cid, "본부장", 2);

        ceo = fixture.employee(cid, company.rootOrgUnitId(), "경영진", false);
        kang = fixture.employee(cid, devDivision, "직원", false);
        seo = fixture.employee(cid, frontendTeam, "직원", false);
        cho = fixture.employee(cid, frontendTeam, "직원", false);
        yoon = fixture.employee(cid, backendTeam, "직원", false);
        jdbc.update("UPDATE employee SET job_title_id = ? WHERE id = ?", ceoTitle, ceo.id());
        jdbc.update("UPDATE employee SET job_title_id = ? WHERE id = ?", divisionHeadTitle, kang.id());
        fixture.lead(cid, company.rootOrgUnitId(), ceo.id());
        fixture.lead(cid, devDivision, kang.id());
        fixture.lead(cid, frontendTeam, seo.id());
        fixture.lead(cid, backendTeam, yoon.id());

        // 휴가 기본 승인선에 2단계 추가
        long leaveDefault = jdbc.queryForObject(
                "SELECT id FROM approval_line WHERE company_id = ? AND work_type = 'LEAVE' AND is_default", Long.class, cid);
        jdbc.update("""
                INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type, up_levels)
                VALUES (?, ?, 2, 'ORG_LEAD_UP', 1)""", cid, leaveDefault);
        long headLine = jdbc.queryForObject("""
                INSERT INTO approval_line (company_id, name, work_type, cond_job_title_id, is_default, priority)
                VALUES (?, '본부장 휴가', 'LEAVE', ?, FALSE, 1) RETURNING id""", Long.class, cid, divisionHeadTitle);
        jdbc.update("""
                INSERT INTO approval_line_step (company_id, approval_line_id, step_order, approver_type, job_title_id)
                VALUES (?, ?, 1, 'JOB_TITLE', ?)""", cid, headLine, ceoTitle);
    }

    private static long title(JdbcTemplate jdbc, long companyId, String name, int order) {
        return jdbc.queryForObject("INSERT INTO job_title (company_id, name, sort_order) VALUES (?, ?, ?) RETURNING id",
                Long.class, companyId, name, order);
    }
}
