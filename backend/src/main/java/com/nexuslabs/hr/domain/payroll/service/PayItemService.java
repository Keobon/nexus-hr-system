package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.PayItemRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayItemResponse;
import com.nexuslabs.hr.domain.payroll.entity.AttendanceBasis;
import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItem;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;
import com.nexuslabs.hr.domain.payroll.repository.PayItemRepository;
import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.audit.AuditLogger;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.request.PatchRequest;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 급여 항목 관리(F-PAY-01, BR-PAY-006). 계산 방식마다 받는 값은 API 설계서 10.1 "계산 방식별 입력" 표를 따르고,
 * 해당 없는 값은 무시하고 null 로 저장한다(역할 분담 v2 2.3 F 35–38). 수정은 다음 정산부터 쓰인다 — 명세서는 저장된 값이다.
 * 등록 · 수정 · 삭제는 감사 로그를 남긴다(BR-AUDIT-001).
 */
@Service
public class PayItemService {

    private static final String AUDIT_TARGET = "PAY_ITEM";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_MULTIPLIER = new BigDecimal("99.99");

    /** PATCH 로 바꿀 수 있는 필드(요청 본문의 JSON 이름). */
    private static final Set<String> FIELDS = Set.of("name", "itemKind", "isTaxable", "nonTaxableLimit", "calcMethod",
            "applyTo", "defaultAmount", "baseRate", "employeeRate", "companyRate", "baseUpperLimit", "baseLowerLimit",
            "attendanceBasis", "multiplier", "inOrdinaryWage", "sortOrder", "isActive");
    private static final Set<String> NOT_NULL = Set.of("name", "itemKind", "isTaxable", "calcMethod", "applyTo",
            "inOrdinaryWage", "sortOrder", "isActive");

    private final PayItemRepository repository;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PayItemService(PayItemRepository repository, JdbcTemplate jdbc, AuditLogger auditLogger,
                          ObjectMapper objectMapper, Validator validator) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** 정렬 순서대로. activeOnly 면 활성만. */
    @Transactional(readOnly = true)
    public List<PayItemResponse> list(long companyId, boolean activeOnly) {
        Set<Long> used = usedIds(companyId);
        return repository.findAll().stream()
                .filter(item -> !activeOnly || item.isActive())
                .sorted(Comparator.comparingInt(PayItem::getSortOrder).thenComparing(PayItem::getId))
                .map(item -> PayItemResponse.from(item, used.contains(item.getId())))
                .toList();
    }

    @Transactional
    public PayItemResponse create(LoginUser user, PayItemRequest request) {
        Values v = normalize(request, true);
        if (repository.existsByName(v.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        checkSingleActive(v, null);
        int sortOrder = v.sortOrder() != null ? v.sortOrder() : jdbc.queryForObject(
                "SELECT COALESCE(max(sort_order), 0) + 1 FROM pay_item WHERE company_id = ?",
                Integer.class, user.companyId());
        PayItem item = new PayItem(v.name(), v.itemKind(), v.calcMethod(), sortOrder);
        apply(item, v, sortOrder);
        PayItemResponse created = PayItemResponse.from(repository.save(item), false);
        auditLogger.log(user, AuditAction.CREATE, AUDIT_TARGET, created.id(), null, created);
        return created;
    }

    /**
     * API 설계서 1.1 PATCH — 보낸 필드만 바꾼다. 현재 값에 병합한 결과를 등록과 같은 규칙으로 검사한다.
     * 쓰인 항목의 구분 · 계산 방식 · 적용 대상을 바꾸면 ITEM_IN_USE.
     */
    @Transactional
    public PayItemResponse update(LoginUser user, long id, Map<String, Object> patch) {
        PayItem item = get(id);
        boolean inUse = inUse(user.companyId(), id);
        PayItemResponse before = PayItemResponse.from(item, inUse);
        Values v = normalize(merge(before, patch), false);
        if (repository.existsByNameAndIdNot(v.name(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
        if (inUse && (v.itemKind() != item.getItemKind() || v.calcMethod() != item.getCalcMethod()
                || v.applyTo() != item.getApplyTo())) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE, "쓰인 항목은 구분 · 계산 방식 · 적용 대상을 바꿀 수 없습니다");
        }
        checkSingleActive(v, id);
        apply(item, v, v.sortOrder());
        PayItemResponse after = PayItemResponse.from(item, inUse);
        auditLogger.log(user, AuditAction.UPDATE, AUDIT_TARGET, id, before, after);
        return after;
    }

    /** 쓰인 적 있으면 비활성화(DEACTIVATED), 없으면 삭제(DELETED). */
    @Transactional
    public DeleteResult delete(LoginUser user, long id) {
        PayItem item = get(id);
        boolean inUse = inUse(user.companyId(), id);
        PayItemResponse before = PayItemResponse.from(item, inUse);
        if (inUse) {
            item.deactivate();
            auditLogger.log(user, AuditAction.UPDATE, AUDIT_TARGET, id, before, PayItemResponse.from(item, true));
            return DeleteResult.deactivated();
        }
        repository.delete(item);
        auditLogger.log(user, AuditAction.DELETE, AUDIT_TARGET, id, before, null);
        return DeleteResult.deleted();
    }

    // ------------------------------------------------------------------

    private PayItem get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 명세서 줄이나 직원별 항목 이력이 이 항목을 가리킨다. */
    private boolean inUse(long companyId, long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM paystub_line WHERE company_id = ? AND pay_item_id = ?)
                            OR EXISTS (SELECT 1 FROM employee_pay_item WHERE company_id = ? AND pay_item_id = ?)
                        """,
                Boolean.class, companyId, id, companyId, id));
    }

    private Set<Long> usedIds(long companyId) {
        return new HashSet<>(jdbc.queryForList("""
                        SELECT pay_item_id FROM paystub_line WHERE company_id = ?
                        UNION SELECT pay_item_id FROM employee_pay_item WHERE company_id = ?
                        """,
                Long.class, companyId, companyId));
    }

    /** 근거 시간마다 · 출장 경비는 회사에 활성 항목 1개(ERD 부분 UK). 다시 켜는 경우도 같다. */
    private void checkSingleActive(Values v, Long exceptId) {
        if (!v.active()) {
            return;
        }
        boolean taken = repository.findAll().stream()
                .filter(other -> other.isActive() && !Objects.equals(other.getId(), exceptId))
                .anyMatch(other -> v.calcMethod() == PayCalcMethod.ATTENDANCE
                        ? other.getCalcMethod() == PayCalcMethod.ATTENDANCE
                        && other.getAttendanceBasis() == v.attendanceBasis()
                        : v.calcMethod() == PayCalcMethod.TRIP_EXPENSE
                        && other.getCalcMethod() == PayCalcMethod.TRIP_EXPENSE);
        if (taken) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    v.calcMethod() == PayCalcMethod.ATTENDANCE
                            ? "같은 근거 시간의 근태 연동 항목이 이미 있습니다"
                            : "출장 경비 항목은 하나만 쓸 수 있습니다");
        }
    }

    private static void apply(PayItem item, Values v, int sortOrder) {
        item.apply(v.name(), v.itemKind(), v.taxable(), v.nonTaxableLimit(), v.calcMethod(), v.applyTo(),
                v.defaultAmount(), v.baseRate(), v.employeeRate(), v.companyRate(), v.baseUpperLimit(),
                v.baseLowerLimit(), v.attendanceBasis(), v.multiplier(), v.inOrdinaryWage(), sortOrder, v.active());
    }

    private PayItemRequest merge(PayItemResponse current, Map<String, Object> patch) {
        PatchRequest.check(patch, FIELDS, NOT_NULL);
        PayItemRequest base = new PayItemRequest(current.name(), current.itemKind(), current.taxable(),
                current.nonTaxableLimit(), current.calcMethod(), current.applyTo(), current.defaultAmount(),
                current.baseRate(), current.employeeRate(), current.companyRate(), current.baseUpperLimit(),
                current.baseLowerLimit(), current.attendanceBasis(), current.multiplier(), current.inOrdinaryWage(),
                current.sortOrder(), current.active());
        Map<String, Object> merged = new LinkedHashMap<>(
                objectMapper.convertValue(base, new TypeReference<Map<String, Object>>() {}));
        merged.putAll(patch);
        PayItemRequest request;
        try {
            request = objectMapper.convertValue(merged, PayItemRequest.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<ConstraintViolation<PayItemRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(cv -> fields.putIfAbsent(cv.getPropertyPath().toString(), cv.getMessage()));
            throw BusinessException.invalidFields(fields);
        }
        return request;
    }

    /**
     * 계산 방식별 입력 표(API 설계서 10.1)대로 맞춘다 — 필요한 값이 없거나 범위를 벗어나면 VALIDATION_ERROR(fields),
     * 해당 없는 값은 null. 공제 항목은 항상 과세, 비과세 한도 · 통상임금은 그 값을 쓸 수 있는 지급 항목만.
     */
    private static Values normalize(PayItemRequest r, boolean creating) {
        Map<String, String> errors = new LinkedHashMap<>();
        PayCalcMethod method = r.calcMethod();
        PayItemKind kind = r.itemKind();
        PayApplyTo applyTo = r.applyTo() != null ? r.applyTo() : PayApplyTo.ALL;

        switch (method) {
            case TAXABLE_RATE -> requireKind(kind, PayItemKind.DEDUCTION, "과세액 대비 요율은 공제 항목만", errors);
            case TRIP_EXPENSE -> requireKind(kind, PayItemKind.EARNING, "출장 경비는 지급 항목만", errors);
            case ATTENDANCE -> {
                if (r.attendanceBasis() == null) {
                    errors.put("attendanceBasis", "근거 시간을 고르세요");
                } else {
                    requireKind(kind, r.attendanceBasis() == AttendanceBasis.ABSENCE ? PayItemKind.DEDUCTION
                            : PayItemKind.EARNING, "결근은 공제, 나머지 근거 시간은 지급 항목입니다", errors);
                }
            }
            default -> { }
        }
        if (applyTo == PayApplyTo.SELECTED && method != PayCalcMethod.FIXED) {
            errors.put("applyTo", "지정 직원은 고정액 항목만 쓸 수 있습니다");
        }

        Long defaultAmount = null;
        if (method == PayCalcMethod.FIXED && applyTo == PayApplyTo.ALL) {
            defaultAmount = r.defaultAmount();
            if (defaultAmount == null || defaultAmount < 0) {
                errors.put("defaultAmount", "금액은 0 이상이어야 합니다");
            }
        }
        BigDecimal baseRate = null;
        if (method == PayCalcMethod.BASE_RATE) {
            baseRate = r.baseRate();
            if (baseRate == null || baseRate.signum() <= 0 || baseRate.compareTo(HUNDRED) > 0 || baseRate.scale() > 4) {
                errors.put("baseRate", "요율은 0 초과 100 이하(소수 넷째 자리까지)여야 합니다");
            }
        }
        BigDecimal employeeRate = null;
        BigDecimal companyRate = null;
        Long upper = null;
        Long lower = null;
        if (method == PayCalcMethod.TAXABLE_RATE) {
            employeeRate = rate("employeeRate", r.employeeRate(), errors);
            companyRate = rate("companyRate", r.companyRate(), errors);
            upper = r.baseUpperLimit();
            lower = r.baseLowerLimit();
            if (upper != null && upper < 0) {
                errors.put("baseUpperLimit", "0 이상이어야 합니다");
            }
            if (lower != null && lower < 0) {
                errors.put("baseLowerLimit", "0 이상이어야 합니다");
            }
            if (upper != null && lower != null && lower > upper) {
                errors.put("baseLowerLimit", "하한액은 상한액 이하여야 합니다");
            }
        }
        AttendanceBasis basis = null;
        BigDecimal multiplier = null;
        if (method == PayCalcMethod.ATTENDANCE) {
            basis = r.attendanceBasis();
            multiplier = r.multiplier();
            if (multiplier == null || multiplier.signum() <= 0 || multiplier.compareTo(MAX_MULTIPLIER) > 0
                    || multiplier.stripTrailingZeros().scale() > 2) {
                errors.put("multiplier", "배율은 0 초과 99.99 이하(소수 둘째 자리까지)여야 합니다");
            }
        }

        boolean earning = kind == PayItemKind.EARNING;
        boolean taxable = !earning || (r.taxable() != null ? r.taxable() : method != PayCalcMethod.TRIP_EXPENSE);
        if (earning && method == PayCalcMethod.ATTENDANCE) {
            taxable = true;
        }
        Long nonTaxableLimit = taxable ? null : r.nonTaxableLimit();
        if (nonTaxableLimit != null && nonTaxableLimit < 0) {
            errors.put("nonTaxableLimit", "0 이상이어야 합니다");
        }
        boolean ordinaryAllowed = earning && (method == PayCalcMethod.FIXED || method == PayCalcMethod.BASE_RATE);
        boolean inOrdinaryWage = ordinaryAllowed && Boolean.TRUE.equals(r.inOrdinaryWage());

        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
        return new Values(r.name().trim(), kind, taxable, nonTaxableLimit, method, applyTo, defaultAmount, baseRate,
                employeeRate, companyRate, upper, lower, basis, multiplier, inOrdinaryWage, r.sortOrder(),
                creating ? !Boolean.FALSE.equals(r.active()) : Boolean.TRUE.equals(r.active()));
    }

    private static void requireKind(PayItemKind actual, PayItemKind expected, String message,
                                    Map<String, String> errors) {
        if (actual != expected) {
            errors.put("itemKind", message);
        }
    }

    private static BigDecimal rate(String field, BigDecimal value, Map<String, String> errors) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.scale() > 4) {
            errors.put(field, "요율은 0 이상 100 이하(소수 넷째 자리까지)여야 합니다");
        }
        return value;
    }

    private record Values(String name, PayItemKind itemKind, boolean taxable, Long nonTaxableLimit,
                          PayCalcMethod calcMethod, PayApplyTo applyTo, Long defaultAmount, BigDecimal baseRate,
                          BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit, Long baseLowerLimit,
                          AttendanceBasis attendanceBasis, BigDecimal multiplier, boolean inOrdinaryWage,
                          Integer sortOrder, boolean active) {
    }
}
