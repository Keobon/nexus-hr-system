package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.PayItemResponse;
import com.nexuslabs.hr.domain.payroll.dto.TaxBrackets;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 소득세 구간(F-PAY-02, BR-PAY-007). 전체 교체만 있다(덮어쓰기, 다음 정산부터 반영). 감사 로그(BR-AUDIT-001).
 */
@Service
public class TaxBracketService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public TaxBracketService(JdbcTemplate jdbc, AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public TaxBrackets get(long companyId) {
        return new TaxBrackets(jdbc.query("""
                        SELECT lower_bound, upper_bound, rate, progressive_deduction FROM tax_bracket
                        WHERE company_id = ? ORDER BY lower_bound
                        """,
                (rs, i) -> new TaxBrackets.Bracket(rs.getLong("lower_bound"), rs.getObject("upper_bound", Long.class),
                        PayItemResponse.plain(rs.getBigDecimal("rate")), rs.getLong("progressive_deduction")),
                companyId));
    }

    /** 0원부터 빈틈 · 겹침 없이, 마지막만 upperBound null, 세율 0–100, 누진공제 0 이상 — 아니면 TAX_BRACKET_INVALID. */
    @Transactional
    public TaxBrackets replace(LoginUser user, TaxBrackets request) {
        List<TaxBrackets.Bracket> brackets = request.brackets().stream()
                .sorted(Comparator.comparingLong(TaxBrackets.Bracket::lowerBound)).toList();
        check(brackets);
        // 동시에 저장하면 뒤 요청의 DELETE 가 앞 요청의 새 행을 못 보고 INSERT 가 유일 제약에 걸린다 — 회사 행으로 줄 세운다
        jdbc.query("SELECT id FROM company WHERE id = ? FOR UPDATE", (rs, i) -> rs.getLong(1), user.companyId());
        TaxBrackets before = get(user.companyId());
        jdbc.update("DELETE FROM tax_bracket WHERE company_id = ?", user.companyId());
        jdbc.batchUpdate("""
                        INSERT INTO tax_bracket (company_id, lower_bound, upper_bound, rate, progressive_deduction)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                brackets.stream().map(b -> new Object[]{user.companyId(), b.lowerBound(), b.upperBound(),
                        PayItemResponse.plain(b.rate()), b.progressiveDeduction()}).toList());
        TaxBrackets after = get(user.companyId());
        auditLogger.log(user, AuditAction.UPDATE, "TAX_BRACKET", null, before, after);
        return after;
    }

    private static void check(List<TaxBrackets.Bracket> brackets) {
        long expectedLower = 0;
        for (int i = 0; i < brackets.size(); i++) {
            TaxBrackets.Bracket b = brackets.get(i);
            boolean last = i == brackets.size() - 1;
            boolean ok = b.lowerBound() == expectedLower
                    && (last ? b.upperBound() == null : b.upperBound() != null && b.upperBound() > b.lowerBound())
                    && b.rate().signum() >= 0 && b.rate().compareTo(HUNDRED) <= 0 && b.rate().scale() <= 2
                    && b.progressiveDeduction() >= 0;
            if (!ok) {
                throw new BusinessException(ErrorCode.TAX_BRACKET_INVALID);
            }
            expectedLower = last ? expectedLower : b.upperBound();
        }
    }
}
