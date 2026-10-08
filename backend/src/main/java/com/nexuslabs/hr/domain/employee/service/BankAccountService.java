package com.nexuslabs.hr.domain.employee.service;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.employee.repository.EmployeeRepository;
import com.nexuslabs.hr.domain.employee.dto.BankAccountRequest;
import com.nexuslabs.hr.domain.employee.dto.BankAccountView;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.crypto.AesCrypto;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 급여 계좌(F-PAY-03, BR-PAY-014). 계좌번호는 AES 로 암호화해 저장하고 뒤 4자리는 따로 둔다. 전체 번호는 본인과 PAYROLL_MANAGE 만 본다.
 * 변경(본인 · 관리자)과 관리자의 전체 번호 조회는 감사 로그 — 로그에는 가린 번호만 남긴다.
 * 급여 대상이 아닌 직원도 등록할 수 있고(F-PAY-03 의 "대상 아님 → 거부"는 기본 급여 · 직원별 항목만), 퇴직자도 관리자는 고칠 수 있다(퇴직 월 정산 지급).
 */
@Service
public class BankAccountService {

    private static final String AUDIT_TARGET = "BANK_ACCOUNT";
    private static final Pattern ACCOUNT_NO = Pattern.compile("\\d+(-\\d+)*");
    private static final int MIN_DIGITS = 6;
    private static final int MAX_DIGITS = 20;

    private final EmployeeRepository employeeRepository;
    private final AesCrypto crypto;
    private final AuditLogger auditLogger;

    public BankAccountService(EmployeeRepository employeeRepository, AesCrypto crypto, AuditLogger auditLogger) {
        this.employeeRepository = employeeRepository;
        this.crypto = crypto;
        this.auditLogger = auditLogger;
    }

    /** 관리자 조회(PAYROLL_MANAGE). reveal 이면 전체 번호 + 감사 로그(VIEW). 등록 전이면 null. */
    @Transactional
    public BankAccountView of(LoginUser user, long employeeId, boolean reveal) {
        Employee employee = find(employeeId);
        BankAccountView view = view(employee, reveal);
        if (reveal && view != null) {
            auditLogger.log(user, AuditAction.VIEW, AUDIT_TARGET, employeeId, null, summary(employeeId, view));
        }
        return view;
    }

    /** 본인 조회 — 전체 번호를 볼 수 있고 감사 로그는 남기지 않는다. */
    @Transactional(readOnly = true)
    public BankAccountView mine(LoginUser user, boolean reveal) {
        return view(find(user.employeeId()), reveal);
    }

    /** 관리자 등록 · 변경(PAYROLL_MANAGE). */
    @Transactional
    public BankAccountView update(LoginUser user, long employeeId, BankAccountRequest request) {
        return save(user, find(employeeId), request);
    }

    /** 본인 등록 · 변경. */
    @Transactional
    public BankAccountView updateMine(LoginUser user, BankAccountRequest request) {
        return save(user, find(user.employeeId()), request);
    }

    private BankAccountView save(LoginUser user, Employee employee, BankAccountRequest request) {
        String accountNo = request.accountNo().trim();
        String digits = accountNo.replace("-", "");
        if (!ACCOUNT_NO.matcher(accountNo).matches() || digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS) {
            throw BusinessException.invalidFields(Map.of("accountNo",
                    "숫자와 하이픈으로 입력하세요(숫자 " + MIN_DIGITS + "~" + MAX_DIGITS + "자리)"));
        }
        BankAccountView before = view(employee, false);
        employee.changeBankAccount(request.bankName().trim(), crypto.encrypt(accountNo),
                digits.substring(digits.length() - 4), request.holder().trim());
        BankAccountView after = view(employee, false);
        auditLogger.log(user, AuditAction.UPDATE, AUDIT_TARGET, employee.getId(),
                before == null ? null : summary(employee.getId(), before), summary(employee.getId(), after));
        return after;
    }

    private Employee find(long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private BankAccountView view(Employee employee, boolean reveal) {
        if (employee.getBankAccountEnc() == null) {
            return null;
        }
        return new BankAccountView(employee.getBankName(), "***-****-" + employee.getBankAccountLast4(),
                reveal ? crypto.decrypt(employee.getBankAccountEnc()) : null, employee.getBankAccountHolder());
    }

    /** 감사 로그용 — 전체 번호는 넣지 않는다. */
    private static Map<String, Object> summary(long employeeId, BankAccountView view) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("employeeId", employeeId);
        value.put("bankName", view.bankName());
        value.put("accountNoMasked", view.accountNoMasked());
        value.put("holder", view.holder());
        return value;
    }
}
