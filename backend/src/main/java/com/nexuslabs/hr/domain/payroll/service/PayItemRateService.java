package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.InsuranceRateRequest;
import com.nexuslabs.hr.domain.payroll.dto.InsuranceRateView;
import com.nexuslabs.hr.domain.payroll.dto.PayItemResponse;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import com.nexuslabs.hr.domain.payroll.entity.PayItemRate;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRateRepository;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 보험 요율 · 기준 상한 · 하한 이력(2026-11-04). 값은 해마다 바뀌므로 적용 시작일과 함께 쌓고, 정산은 귀속 월 말일에 유효한 값을
 * 쓴다 — 아직 정산하지 않은 지난달까지 바뀌지 않는다. 유효 값 규칙은 {@link PayVariableService#valueOn}과 같다.
 */
@Service
public class PayItemRateService {

    private static final String AUDIT_TARGET = "PAY_ITEM_RATE";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PayItemRateRepository repository;
    private final PayItemRepository payItemRepository;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public PayItemRateService(PayItemRateRepository repository, PayItemRepository payItemRepository, JdbcTemplate jdbc,
                              AuditLogger auditLogger, Clock clock) {
        this.repository = repository;
        this.payItemRepository = payItemRepository;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 요율 행의 값. */
    public record Rate(BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit, Long baseLowerLimit) {
    }

    /**
     * 항목 ID → 그날 유효한 값 — 적용 시작일이 그날 이전인 마지막 행. 그날보다 이른 행이 없으면(회사 등록 전 날짜) 가장 이른 행.
     * 같은 적용 시작일이 여럿이면 나중에 만든 행.
     */
    @Transactional(readOnly = true)
    public Map<Long, Rate> ratesOn(long companyId, LocalDate date) {
        Map<Long, Rate> result = new HashMap<>();
        jdbc.query("""
                        SELECT DISTINCT ON (pay_item_id) pay_item_id, employee_rate, company_rate, base_upper_limit,
                               base_lower_limit
                        FROM pay_item_rate WHERE company_id = ?
                        ORDER BY pay_item_id, (effective_from <= ?) DESC,
                                 CASE WHEN effective_from <= ? THEN effective_from END DESC,
                                 effective_from, created_at DESC, id DESC
                        """,
                rs -> {
                    result.put(rs.getLong("pay_item_id"), new Rate(rs.getBigDecimal("employee_rate"),
                            rs.getBigDecimal("company_rate"), rs.getObject("base_upper_limit", Long.class),
                            rs.getObject("base_lower_limit", Long.class)));
                },
                companyId, Date.valueOf(date), Date.valueOf(date));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, Rate> ratesToday(long companyId) {
        return ratesOn(companyId, LocalDate.now(clock));
    }

    /** 활성 요율 항목마다 현재 · 예정 · 이력. 정렬 순서대로. */
    @Transactional(readOnly = true)
    public List<InsuranceRateView> list(long companyId) {
        Map<Long, List<StoredRow>> rows = new HashMap<>();
        jdbc.query("""
                        SELECT pay_item_id, employee_rate, company_rate, base_upper_limit, base_lower_limit,
                               effective_from, created_at
                        FROM pay_item_rate WHERE company_id = ? ORDER BY effective_from, created_at, id
                        """,
                rs -> {
                    rows.computeIfAbsent(rs.getLong("pay_item_id"), k -> new ArrayList<>())
                            .add(new StoredRow(rs.getBigDecimal("employee_rate"), rs.getBigDecimal("company_rate"),
                                    rs.getObject("base_upper_limit", Long.class),
                                    rs.getObject("base_lower_limit", Long.class),
                                    rs.getObject("effective_from", LocalDate.class),
                                    rs.getObject("created_at", OffsetDateTime.class)));
                },
                companyId);
        return payItemRepository.findAll().stream()
                .filter(item -> item.isActive() && item.getCalcMethod() == PayCalcMethod.TAXABLE_RATE)
                .sorted(Comparator.comparingInt(PayItem::getSortOrder).thenComparing(PayItem::getId))
                .map(item -> view(item, rows.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    /** 일괄 저장. 같은 적용 시작일의 값이 이미 있으면 새 행이 정정본이 된다. 하나라도 틀리면 fields 로 알리고 아무것도 저장하지 않는다. */
    @Transactional
    public List<InsuranceRateView> save(LoginUser user, InsuranceRateRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.items().size(); i++) {
            InsuranceRateRequest.Item item = request.items().get(i);
            String prefix = "items[%d].".formatted(i);
            boolean rated = item.payItemId() != null && payItemRepository.findById(item.payItemId())
                    .filter(p -> p.isActive() && p.getCalcMethod() == PayCalcMethod.TAXABLE_RATE).isPresent();
            if (!rated) {
                errors.put(prefix + "payItemId", "활성인 요율 항목이 아닙니다");
            } else if (!seen.add(item.payItemId())) {
                errors.put(prefix + "payItemId", "같은 항목을 두 번 보냈습니다");
            }
            validate(prefix, item.employeeRate(), item.companyRate(), item.baseUpperLimit(), item.baseLowerLimit(), errors);
        }
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        for (InsuranceRateRequest.Item item : request.items()) {
            PayItemRate saved = repository.saveAndFlush(new PayItemRate(item.payItemId(), item.employeeRate(),
                    item.companyRate(), item.baseUpperLimit(), item.baseLowerLimit(), request.effectiveFrom(),
                    user.employeeId()));
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("payItemId", item.payItemId());
            after.put("employeeRate", PayItemResponse.plain(item.employeeRate()));
            after.put("companyRate", PayItemResponse.plain(item.companyRate()));
            after.put("baseUpperLimit", item.baseUpperLimit());
            after.put("baseLowerLimit", item.baseLowerLimit());
            after.put("effectiveFrom", request.effectiveFrom().toString());
            auditLogger.log(user, AuditAction.CREATE, AUDIT_TARGET, saved.getId(), null, after);
        }
        return list(user.companyId());
    }

    /** 요율 항목을 새로 만들 때 처음 값 — 오늘부터 유효. */
    @Transactional
    public Rate addFirst(LoginUser user, PayItem item, Rate rate) {
        repository.saveAndFlush(new PayItemRate(item.getId(), rate.employeeRate(), rate.companyRate(),
                rate.baseUpperLimit(), rate.baseLowerLimit(), LocalDate.now(clock), user.employeeId()));
        return rate;
    }

    /** 요율은 0 이상 100 이하(소수 넷째 자리까지), 상한 · 하한은 0 이상이고 하한 ≤ 상한. prefix 는 fields 키 앞부분. */
    static void validate(String prefix, BigDecimal employeeRate, BigDecimal companyRate, Long upper, Long lower,
                         Map<String, String> errors) {
        checkRate(prefix + "employeeRate", employeeRate, errors);
        checkRate(prefix + "companyRate", companyRate, errors);
        if (upper != null && upper < 0) {
            errors.put(prefix + "baseUpperLimit", "0 이상이어야 합니다");
        }
        if (lower != null && lower < 0) {
            errors.put(prefix + "baseLowerLimit", "0 이상이어야 합니다");
        }
        if (upper != null && lower != null && lower > upper) {
            errors.put(prefix + "baseLowerLimit", "하한액은 상한액 이하여야 합니다");
        }
    }

    private static void checkRate(String field, BigDecimal value, Map<String, String> errors) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.stripTrailingZeros().scale() > 4) {
            errors.put(field, "요율은 0 이상 100 이하(소수 넷째 자리까지)여야 합니다");
        }
    }

    private InsuranceRateView view(PayItem item, List<StoredRow> rows) {
        LocalDate today = LocalDate.now(clock);
        // rows 는 적용 시작일 · 만든 시각 · ID 오름차순 — 바로 뒤 행이 같은 날짜면 그 행이 정정본이다
        List<InsuranceRateView.Row> upcoming = new ArrayList<>();
        List<InsuranceRateView.Row> history = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            StoredRow r = rows.get(i);
            boolean superseded = i + 1 < rows.size() && rows.get(i + 1).effectiveFrom().equals(r.effectiveFrom());
            InsuranceRateView.Row row = new InsuranceRateView.Row(PayItemResponse.plain(r.employeeRate()),
                    PayItemResponse.plain(r.companyRate()), r.baseUpperLimit(), r.baseLowerLimit(), r.effectiveFrom(),
                    r.createdAt(), superseded);
            (r.effectiveFrom().isAfter(today) ? upcoming : history).add(row);
        }
        List<InsuranceRateView.Row> latestFirst = new ArrayList<>(history.reversed());
        return new InsuranceRateView(item.getId(), item.getName(), latestFirst.isEmpty() ? null : latestFirst.getFirst(),
                upcoming, latestFirst);
    }

    private record StoredRow(BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit, Long baseLowerLimit,
                             LocalDate effectiveFrom, OffsetDateTime createdAt) {
    }
}
