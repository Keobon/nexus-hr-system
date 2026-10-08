package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.PayItemResponse;
import com.nexuslabs.hr.domain.payroll.dto.PayVariableRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayVariableView;
import com.nexuslabs.hr.domain.payroll.entity.PayVarCode;
import com.nexuslabs.hr.domain.payroll.entity.PayVariable;
import com.nexuslabs.hr.domain.payroll.repository.PayVariableRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.config.ClockConfig;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 계산 변수(F-PAY-02). append-only — 값을 바꾸면 적용 시작일과 함께 새 행을 쌓는다. 과거 날짜도 된다(정산된 명세서는
 * 저장된 값이라 바뀌지 않는다). 정산 · 연봉 분할은 그날 유효한 값({@link #valueOn})을 쓴다.
 */
@Service
public class PayVariableService {

    private static final String AUDIT_TARGET = "PAY_VARIABLE";

    private final PayVariableRepository repository;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public PayVariableService(PayVariableRepository repository, JdbcTemplate jdbc, AuditLogger auditLogger,
                              Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 변수 5개를 정해진 순서로. */
    @Transactional(readOnly = true)
    public List<PayVariableView> list(long companyId) {
        List<StoredRow> rows = rows(companyId);
        return Arrays.stream(PayVarCode.values()).map(code -> view(code, rows)).toList();
    }

    /**
     * 새 값 등록. 같은 적용 시작일의 값이 이미 있으면 이 행이 정정본이 된다(역할 분담 v2 2.3 F-J1 — 잘못 넣은 값은
     * 같은 날짜로 다시 넣어 고친다). 응답은 그 변수의 조회 행.
     */
    @Transactional
    public PayVariableView create(LoginUser user, PayVariableRequest request) {
        validate(request.varCode(), request.value());
        PayVariable saved = repository.saveAndFlush(new PayVariable(request.varCode(),
                PayItemResponse.plain(request.value()), request.effectiveFrom(), user.employeeId()));
        auditLogger.log(user, AuditAction.CREATE, AUDIT_TARGET, saved.getId(), null,
                Map.of("varCode", request.varCode().name(), "value", PayItemResponse.plain(request.value()),
                        "effectiveFrom", request.effectiveFrom().toString()));
        return view(request.varCode(), rows(user.companyId()));
    }

    /**
     * 그날 유효한 값 — 적용 시작일이 그날 이전인 마지막 행. 그날보다 이른 행이 없으면(회사 등록 전 날짜) 가장 이른 행.
     * 같은 적용 시작일이 여럿이면 나중에 만든 행(created_at DESC, id DESC — 같은 트랜잭션이면 created_at 이 같다).
     */
    @Transactional(readOnly = true)
    public BigDecimal valueOn(long companyId, PayVarCode code, LocalDate date) {
        return jdbc.queryForList("""
                        SELECT value FROM pay_variable WHERE company_id = ? AND var_code = ?::pay_var_code
                        ORDER BY (effective_from <= ?) DESC,
                                 CASE WHEN effective_from <= ? THEN effective_from END DESC,
                                 effective_from, created_at DESC, id DESC
                        LIMIT 1
                        """,
                        BigDecimal.class, companyId, code.name(), Date.valueOf(date), Date.valueOf(date))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("계산 변수가 없다: " + code));
    }

    // ------------------------------------------------------------------

    /** 값 범위(역할 분담 v2 2.3 F 39). DB 는 DECIMAL(15,4) · 0 이상만 막는다. */
    private static void validate(PayVarCode code, BigDecimal value) {
        boolean integer = value.stripTrailingZeros().scale() <= 0;
        String error = switch (code) {
            case DEPENDENT_DEDUCTION, MULTI_CHILD_DEDUCTION ->
                    value.signum() < 0 || !integer || value.compareTo(new BigDecimal("100000000")) > 0
                            ? "0 이상 1억 이하의 원 단위 정수여야 합니다" : null;
            case LOCAL_TAX_RATE -> value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0
                    || value.stripTrailingZeros().scale() > 4 ? "0 이상 100 이하(소수 넷째 자리까지)여야 합니다" : null;
            case ANNUAL_SPLIT_MONTHS -> !integer || value.compareTo(BigDecimal.ONE) < 0
                    || value.compareTo(BigDecimal.valueOf(24)) > 0 ? "1 이상 24 이하의 정수여야 합니다" : null;
            case MONTHLY_STANDARD_HOURS -> !integer || value.compareTo(BigDecimal.ONE) < 0
                    || value.compareTo(BigDecimal.valueOf(744)) > 0 ? "1 이상 744 이하의 정수여야 합니다" : null;
        };
        if (error != null) {
            throw BusinessException.invalidFields(Map.of("value", error));
        }
    }

    private PayVariableView view(PayVarCode code, List<StoredRow> rows) {
        LocalDate today = LocalDate.now(clock);
        // rows 는 적용 시작일 · 만든 시각 · ID 오름차순 — 바로 뒤 행이 같은 날짜면 그 행이 정정본이다
        List<StoredRow> mine = rows.stream().filter(r -> r.code() == code).toList();
        List<PayVariableView.Row> upcoming = new ArrayList<>();
        List<PayVariableView.Row> history = new ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            StoredRow r = mine.get(i);
            boolean superseded = i + 1 < mine.size() && mine.get(i + 1).effectiveFrom().equals(r.effectiveFrom());
            PayVariableView.Row row = new PayVariableView.Row(r.value(), r.effectiveFrom(), r.createdAt(), superseded);
            (r.effectiveFrom().isAfter(today) ? upcoming : history).add(row);
        }
        List<PayVariableView.Row> latestFirst = new ArrayList<>(history.reversed());
        return new PayVariableView(code, latestFirst.isEmpty() ? null : latestFirst.getFirst(), upcoming, latestFirst);
    }

    private List<StoredRow> rows(long companyId) {
        return jdbc.query("""
                        SELECT var_code::text AS var_code, value, effective_from, created_at FROM pay_variable
                        WHERE company_id = ? ORDER BY effective_from, created_at, id
                        """,
                (rs, i) -> new StoredRow(PayVarCode.valueOf(rs.getString("var_code")),
                        PayItemResponse.plain(rs.getBigDecimal("value")), rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("created_at", OffsetDateTime.class)
                                .atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime()),
                companyId);
    }

    private record StoredRow(PayVarCode code, BigDecimal value, LocalDate effectiveFrom, OffsetDateTime createdAt) {
    }
}
