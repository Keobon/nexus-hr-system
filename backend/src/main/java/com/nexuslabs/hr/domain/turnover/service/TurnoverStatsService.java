package com.nexuslabs.hr.domain.turnover.service;

import com.nexuslabs.hr.domain.employee.entity.ResignReason;
import com.nexuslabs.hr.domain.org.service.TopOrgGroupReader;
import com.nexuslabs.hr.domain.org.service.TopOrgGroups;
import com.nexuslabs.hr.domain.turnover.dto.TurnoverStats;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionReader;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 퇴사 통계(작업 지시서 2-3 · 2-4). 저장하지 않고 직원의 입사일 · 퇴사일에서 바로 센다(BR-DASH-001). 권한(DASHBOARD_COMPANY)은
 * 컨트롤러가 본다. 회사 직원 수가 많지 않아 직원을 한 번 읽어 메모리에서 센다.
 * 부서는 지금 기록된 소속(퇴사자는 퇴사 당시 소속)을 최상위 바로 아래 조직으로 묶는다 — 발령 이력으로 그날 소속을 되짚지 않는다.
 */
@Service
public class TurnoverStatsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;
    private final TopOrgGroupReader orgGroupReader;

    public TurnoverStatsService(JdbcTemplate jdbc, PermissionReader permissionReader, TopOrgGroupReader orgGroupReader) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
        this.orgGroupReader = orgGroupReader;
    }

    /** 집계 단위. */
    public enum Unit { MONTH, QUARTER, YEAR }

    private record Person(long id, String employeeNo, String name, long orgUnitId, String orgUnitName,
                          String jobGradeName, LocalDate hireDate, LocalDate resignDate, ResignReason reason) {

        boolean employedOn(LocalDate d) {
            return !hireDate.isAfter(d) && (resignDate == null || resignDate.isAfter(d));
        }

        boolean hiredIn(LocalDate start, LocalDate end) {
            return !hireDate.isBefore(start) && !hireDate.isAfter(end);
        }

        boolean resignedIn(LocalDate start, LocalDate end) {
            return resignDate != null && !resignDate.isBefore(start) && !resignDate.isAfter(end);
        }
    }

    /** 한 구간의 수치. */
    private record Period(int hires, int resignations, BigDecimal avgHeadcount, BigDecimal turnoverRate) {
    }

    // ------------------------------------------------------------------ API

    @Transactional(readOnly = true)
    public TurnoverStats.Summary summary(LoginUser user, YearMonth month) {
        List<Person> people = people(user.companyId());
        Period now = period(people, month.atDay(1), month.atEndOfMonth());
        YearMonth prev = month.minusMonths(1);
        Period before = period(people, prev.atDay(1), prev.atEndOfMonth());
        int headcount = (int) people.stream().filter(p -> p.employedOn(month.atEndOfMonth())).count();
        return new TurnoverStats.Summary(month.toString(), headcount, now.hires(), now.resignations(),
                now.turnoverRate(), before.turnoverRate(), now.hires() - now.resignations());
    }

    /** 오래된 순, 데이터가 없는 구간도 0으로. 분기 · 연은 from · to 가 속한 구간 전체(첫날 · 말일). */
    @Transactional(readOnly = true)
    public List<TurnoverStats.TrendPoint> trend(LoginUser user, String unit, YearMonth from, YearMonth to) {
        Unit u = parseUnit(unit);
        checkRange(from, to);
        List<Person> people = people(user.companyId());
        List<TurnoverStats.TrendPoint> points = new ArrayList<>();
        YearMonth cursor = startOf(u, from);
        while (!cursor.isAfter(to)) {
            YearMonth last = cursor.plusMonths(span(u) - 1L);
            Period p = period(people, cursor.atDay(1), last.atEndOfMonth());
            points.add(new TurnoverStats.TrendPoint(label(u, cursor), p.hires(), p.resignations(), p.avgHeadcount(),
                    p.turnoverRate()));
            cursor = cursor.plusMonths(span(u));
        }
        return points;
    }

    /** 최상위 바로 아래 조직(하위 포함)마다, 최상위 직속은 맨 뒤. 평균 재직 · 퇴사가 모두 0인 조직은 뺀다. */
    @Transactional(readOnly = true)
    public List<TurnoverStats.ByDepartment> byDepartment(LoginUser user, YearMonth from, YearMonth to) {
        checkRange(from, to);
        List<Person> people = people(user.companyId());
        TopOrgGroups groups = orgGroupReader.read(user.companyId());
        Map<Long, List<Person>> byGroup = new HashMap<>();
        people.forEach(p -> byGroup.computeIfAbsent(groups.groupOf(p.orgUnitId()), k -> new ArrayList<>()).add(p));
        List<TurnoverStats.ByDepartment> rows = new ArrayList<>();
        for (long g : groups.ordered(byGroup.keySet())) {
            Period p = period(byGroup.get(g), from.atDay(1), to.atEndOfMonth());
            if (p.avgHeadcount().signum() > 0 || p.resignations() > 0) {
                rows.add(new TurnoverStats.ByDepartment(g, groups.name(g), p.avgHeadcount(), p.resignations(),
                        p.turnoverRate()));
            }
        }
        return rows;
    }

    /** 네 구간 모두(0 포함). 1년 미만 · 1~3년 · 3~5년 · 5년 이상. */
    @Transactional(readOnly = true)
    public List<TurnoverStats.ByTenure> byTenure(LoginUser user, YearMonth from, YearMonth to) {
        checkRange(from, to);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String b : List.of("UNDER_1Y", "Y1_3", "Y3_5", "OVER_5Y")) {
            counts.put(b, 0);
        }
        resigned(user.companyId(), from, to).forEach(p -> counts.merge(bucket(p), 1, Integer::sum));
        return counts.entrySet().stream().map(e -> new TurnoverStats.ByTenure(e.getKey(), e.getValue())).toList();
    }

    /** 퇴사자가 있는 사유만, 많은 순(같으면 사유 순서). ratio = 그 사유 ÷ 기간 퇴사자 × 100. */
    @Transactional(readOnly = true)
    public List<TurnoverStats.ByReason> byReason(LoginUser user, YearMonth from, YearMonth to) {
        checkRange(from, to);
        List<Person> resigned = resigned(user.companyId(), from, to);
        Map<ResignReason, Integer> counts = new EnumMap<>(ResignReason.class);
        resigned.forEach(p -> counts.merge(p.reason() == null ? ResignReason.ETC : p.reason(), 1, Integer::sum));
        return Arrays.stream(ResignReason.values()).filter(counts::containsKey)
                .sorted(Comparator.comparing((ResignReason r) -> -counts.get(r)).thenComparing(Enum::ordinal))
                .map(r -> new TurnoverStats.ByReason(r, counts.get(r), rate(counts.get(r), BigDecimal.valueOf(resigned.size()))))
                .toList();
    }

    /** 퇴사일 최근 순(같으면 사번 순). 기본 20명, 최대 100명. EMPLOYEE_MANAGE 가 없으면 이름을 가린다. */
    @Transactional(readOnly = true)
    public PageResponse<TurnoverStats.Resignation> resignations(LoginUser user, YearMonth from, YearMonth to,
                                                               int page, int size) {
        checkRange(from, to);
        boolean showName = permissionReader.permissionsOf(user).containsKey(PermissionCode.EMPLOYEE_MANAGE);
        List<Person> rows = resigned(user.companyId(), from, to).stream()
                .sorted(Comparator.comparing(Person::resignDate).reversed().thenComparing(Person::employeeNo))
                .toList();
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        int start = (int) Math.min((long) pageNo * pageSize, rows.size());
        List<TurnoverStats.Resignation> content = rows.subList(start, Math.min(start + pageSize, rows.size())).stream()
                .map(p -> new TurnoverStats.Resignation(p.id(), showName ? p.name() : mask(p.name()), p.orgUnitName(),
                        p.jobGradeName(), p.hireDate(), p.resignDate(),
                        ChronoUnit.DAYS.between(p.hireDate(), p.resignDate()), p.reason()))
                .toList();
        return new PageResponse<>(content, pageNo, pageSize, rows.size(), (rows.size() + pageSize - 1) / pageSize);
    }

    // ------------------------------------------------------------------ 계산

    private List<Person> people(long cid) {
        return jdbc.query("""
                        SELECT e.id, e.employee_no, e.name, e.org_unit_id, o.name AS org_unit_name, g.name AS job_grade_name,
                               e.hire_date, e.resign_date, e.resign_reason::text AS resign_reason
                        FROM employee e
                             JOIN org_unit o ON o.id = e.org_unit_id AND o.company_id = e.company_id
                             LEFT JOIN job_grade g ON g.id = e.job_grade_id AND g.company_id = e.company_id
                        WHERE e.company_id = ?
                        """,
                (rs, i) -> new Person(rs.getLong("id"), rs.getString("employee_no"), rs.getString("name"),
                        rs.getLong("org_unit_id"), rs.getString("org_unit_name"), rs.getString("job_grade_name"),
                        rs.getObject("hire_date", LocalDate.class), rs.getObject("resign_date", LocalDate.class),
                        rs.getString("resign_reason") == null ? null : ResignReason.valueOf(rs.getString("resign_reason"))),
                cid);
    }

    private List<Person> resigned(long cid, YearMonth from, YearMonth to) {
        return people(cid).stream().filter(p -> p.resignedIn(from.atDay(1), to.atEndOfMonth())).toList();
    }

    private static Period period(List<Person> people, LocalDate start, LocalDate end) {
        int atStart = 0;
        int atEnd = 0;
        int hires = 0;
        int resignations = 0;
        for (Person p : people) {
            atStart += p.employedOn(start) ? 1 : 0;
            atEnd += p.employedOn(end) ? 1 : 0;
            hires += p.hiredIn(start, end) ? 1 : 0;
            resignations += p.resignedIn(start, end) ? 1 : 0;
        }
        BigDecimal avg = BigDecimal.valueOf(atStart + atEnd).divide(TWO, 1, RoundingMode.HALF_UP);
        return new Period(hires, resignations, avg, rate(resignations, avg));
    }

    /** count ÷ base × 100, 소수 첫째 자리. base 가 0 이면 0. */
    private static BigDecimal rate(int count, BigDecimal base) {
        return base.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(count).multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_UP);
    }

    private static String bucket(Person p) {
        if (p.resignDate().isBefore(p.hireDate().plusYears(1))) {
            return "UNDER_1Y";
        }
        if (p.resignDate().isBefore(p.hireDate().plusYears(3))) {
            return "Y1_3";
        }
        if (p.resignDate().isBefore(p.hireDate().plusYears(5))) {
            return "Y3_5";
        }
        return "OVER_5Y";
    }

    /** 홍길동 → 홍*동, 남궁민수 → 남**수, 두 글자는 이수 → 이*. 한 글자는 그대로. */
    static String mask(String name) {
        int[] cps = name.codePoints().toArray();
        if (cps.length <= 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder().appendCodePoint(cps[0]);
        sb.append("*".repeat(cps.length == 2 ? 1 : cps.length - 2));
        if (cps.length > 2) {
            sb.appendCodePoint(cps[cps.length - 1]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 입력

    private static Unit parseUnit(String unit) {
        try {
            return Unit.valueOf(unit.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.invalidFields(Map.of("unit", "month · quarter · year 중 하나여야 합니다"));
        }
    }

    private static void checkRange(YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw BusinessException.invalidFields(Map.of("from", "시작 달은 종료 달보다 늦을 수 없습니다"));
        }
    }

    private static int span(Unit u) {
        return switch (u) {
            case MONTH -> 1;
            case QUARTER -> 3;
            case YEAR -> 12;
        };
    }

    private static YearMonth startOf(Unit u, YearMonth ym) {
        return switch (u) {
            case MONTH -> ym;
            case QUARTER -> YearMonth.of(ym.getYear(), (ym.getMonthValue() - 1) / 3 * 3 + 1);
            case YEAR -> YearMonth.of(ym.getYear(), 1);
        };
    }

    private static String label(Unit u, YearMonth start) {
        return switch (u) {
            case MONTH -> start.toString();
            case QUARTER -> start.getYear() + "-Q" + ((start.getMonthValue() - 1) / 3 + 1);
            case YEAR -> String.valueOf(start.getYear());
        };
    }
}
