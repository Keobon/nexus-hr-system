package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.dto.PayItemResponse;
import com.nexuslabs.hr.domain.payroll.entity.AttendanceBasis;
import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 직원 1명 × 귀속 월의 명세서 계산(F-PAY-05 13단계, 백엔드 안내 6.2). <b>저장하지 않는 순수 계산</b>이라
 * 정산 미리보기와 확정이 같은 결과를 낸다. 금액은 단계 · 항목마다 원 미만 내림, 시간은 분으로 계산하고 마지막에 ÷ 60.
 * 백엔드 안내 6.3 급여 예제가 단위 테스트로 고정돼 있다.
 */
public final class PayrollCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private PayrollCalculator() {
    }

    /** 정산에 쓰는 급여 항목 하나(정산하는 순간의 활성 항목). */
    public record Item(long id, String name, PayItemKind kind, PayCalcMethod method, PayApplyTo applyTo,
                       boolean taxable, Long nonTaxableLimit, Long defaultAmount, BigDecimal baseRate,
                       BigDecimal employeeRate, BigDecimal companyRate, Long baseUpperLimit, Long baseLowerLimit,
                       AttendanceBasis basis, BigDecimal multiplier, boolean inOrdinaryWage, int sortOrder) {
    }

    /** 회사 설정 — 계산 변수는 귀속 월 말일에 유효한 값. */
    public record Settings(List<Item> items, BigDecimal monthlyStandardHours, long dependentDeduction,
                           long multiChildDeduction, BigDecimal localTaxRate, List<Bracket> brackets) {
    }

    /** 소득세 구간 [lowerBound, upperBound). upperBound null 은 끝없음. */
    public record Bracket(long lowerBound, Long upperBound, BigDecimal rate, long progressiveDeduction) {
    }

    /**
     * 직원 한 명의 입력.
     *
     * @param monthlyBase     귀속 월 말일에 유효한 월 기본급(일할 전)
     * @param selectedAmounts 지정 직원 항목 ID → 귀속 월 말일에 유효한 금액
     * @param manualAmounts   수동 입력 항목 ID → 금액
     * @param minutes         근거 시간 → 분(결근은 결근한 날의 소정근로시간 합)
     * @param absentDays      결근 일수(결근 줄 산식 표시용)
     * @param expenseTotal    반영할 출장 경비 합계
     */
    public record Input(long monthlyBase, int workedDays, int monthDays, Map<Long, Long> selectedAmounts,
                        Map<Long, Long> manualAmounts, Map<AttendanceBasis, Integer> minutes, int absentDays,
                        long expenseTotal, int dependentsCount, int childrenCount) {
    }

    /** 명세서 줄. 공제 줄은 taxable · nonTaxable 이 0, companyAmount 는 과세액 대비 요율 항목만. */
    public record Line(long payItemId, String name, PayItemKind itemKind, PayCalcMethod calcMethod, long amount,
                       long taxableAmount, long nonTaxableAmount, Long companyAmount, BigDecimal quantity,
                       Long unitPrice, String formulaNote, int sortOrder) {
    }

    /** totalDeduction = 공제 줄 합계 + 소득세 + 지방소득세, netPay = grossPay − totalDeduction (DB CHECK 와 같다). */
    public record Result(long basePay, long ordinaryHourlyWage, List<Line> lines, long grossPay, long taxablePay,
                         long incomeTax, long localIncomeTax, long totalDeduction, long netPay,
                         long companyBurdenTotal) {
    }

    public static Result calculate(Settings settings, Input in) {
        List<Item> items = settings.items().stream()
                .sorted(Comparator.comparingInt(Item::sortOrder).thenComparingLong(Item::id)).toList();

        // 2. 통상시급 = (기본급 + 통상임금 포함 항목) ÷ 월 소정근로시간 — 일할 전 금액
        long ordinary = in.monthlyBase();
        for (Item item : items) {
            if (item.inOrdinaryWage() && item.kind() == PayItemKind.EARNING) {
                ordinary += fullAmount(item, in);
            }
        }
        long hourly = BigDecimal.valueOf(ordinary).divide(settings.monthlyStandardHours(), 0, RoundingMode.FLOOR)
                .longValueExact();

        // 1 · 3 · 4. 기본급과 지급 항목(고정액 · 기본급 대비 % 는 일할)
        long basePay = prorate(in.monthlyBase(), in);
        List<Line> lines = new ArrayList<>();
        long gross = basePay;
        long nonTaxableTotal = 0;
        for (Item item : items) {
            if (item.kind() != PayItemKind.EARNING) {
                continue;
            }
            Line line = earningLine(item, in, hourly);
            if (line == null) {
                continue;
            }
            lines.add(line);
            gross += line.amount();
            nonTaxableTotal += line.nonTaxableAmount();
        }

        // 6. 과세액
        long taxable = gross - nonTaxableTotal;

        // 7 · 8. 공제 — 요율 공제와 회사부담, 그 밖의 공제
        long deductions = 0;
        long companyBurden = 0;
        for (Item item : items) {
            if (item.kind() != PayItemKind.DEDUCTION) {
                continue;
            }
            Line line = deductionLine(item, in, hourly, taxable);
            if (line == null) {
                continue;
            }
            lines.add(line);
            deductions += line.amount();
            companyBurden += line.companyAmount() == null ? 0 : line.companyAmount();
        }
        lines.sort(Comparator.comparingInt(Line::sortOrder).thenComparingLong(Line::payItemId));

        // 9–11. 과세표준 · 소득세 · 지방소득세
        long multiChild = in.childrenCount() >= 2 ? (in.childrenCount() - 1L) * settings.multiChildDeduction() : 0;
        long taxBase = Math.max(0, taxable - in.dependentsCount() * settings.dependentDeduction() - multiChild);
        long incomeTax = incomeTax(taxBase, settings.brackets());
        long localTax = floor(BigDecimal.valueOf(incomeTax).multiply(settings.localTaxRate()).divide(HUNDRED));

        // 12. 실지급액
        long totalDeduction = deductions + incomeTax + localTax;
        return new Result(basePay, hourly, lines, gross, taxable, incomeTax, localTax, totalDeduction,
                gross - totalDeduction, companyBurden);
    }

    // ------------------------------------------------------------------

    /** 고정액 · 기본급 대비 % 의 일할 전 금액. */
    private static long fullAmount(Item item, Input in) {
        return switch (item.method()) {
            case FIXED -> item.applyTo() == PayApplyTo.ALL ? nz(item.defaultAmount())
                    : in.selectedAmounts().getOrDefault(item.id(), 0L);
            case BASE_RATE -> floor(BigDecimal.valueOf(in.monthlyBase()).multiply(item.baseRate()).divide(HUNDRED));
            default -> 0;
        };
    }

    private static Line earningLine(Item item, Input in, long hourly) {
        long amount;
        BigDecimal quantity = null;
        Long unitPrice = null;
        String formula = null;
        switch (item.method()) {
            case FIXED, BASE_RATE -> amount = prorate(fullAmount(item, in), in);
            case MANUAL -> amount = in.manualAmounts().getOrDefault(item.id(), 0L);
            case TRIP_EXPENSE -> amount = in.expenseTotal();
            case ATTENDANCE -> {
                int minutes = in.minutes().getOrDefault(item.basis(), 0);
                amount = attendanceAmount(hourly, item.multiplier(), minutes);
                quantity = hours(minutes);
                unitPrice = floor(BigDecimal.valueOf(hourly).multiply(item.multiplier()));
                formula = formula(minutes, 0, hourly, item.multiplier());
            }
            default -> amount = 0;
        }
        if (amount <= 0) {
            return null;
        }
        long nonTaxable = item.taxable() ? 0
                : item.nonTaxableLimit() == null ? amount : Math.min(amount, item.nonTaxableLimit());
        return new Line(item.id(), item.name(), item.kind(), item.method(), amount, amount - nonTaxable, nonTaxable,
                null, quantity, unitPrice, formula, item.sortOrder());
    }

    private static Line deductionLine(Item item, Input in, long hourly, long taxable) {
        long amount;
        Long companyAmount = null;
        BigDecimal quantity = null;
        Long unitPrice = null;
        String formula = null;
        switch (item.method()) {
            case FIXED, BASE_RATE -> amount = prorate(fullAmount(item, in), in);
            case MANUAL -> amount = in.manualAmounts().getOrDefault(item.id(), 0L);
            case TAXABLE_RATE -> {
                long base = taxable;
                if (item.baseLowerLimit() != null) {
                    base = Math.max(base, item.baseLowerLimit());
                }
                if (item.baseUpperLimit() != null) {
                    base = Math.min(base, item.baseUpperLimit());
                }
                amount = floor(BigDecimal.valueOf(base).multiply(item.employeeRate()).divide(HUNDRED));
                companyAmount = floor(BigDecimal.valueOf(base).multiply(item.companyRate()).divide(HUNDRED));
            }
            case ATTENDANCE -> {
                int minutes = in.minutes().getOrDefault(item.basis(), 0);
                amount = attendanceAmount(hourly, item.multiplier(), minutes);
                quantity = hours(minutes);
                unitPrice = floor(BigDecimal.valueOf(hourly).multiply(item.multiplier()));
                formula = formula(minutes, item.basis() == AttendanceBasis.ABSENCE ? in.absentDays() : 0, hourly,
                        item.multiplier());
            }
            default -> amount = 0;
        }
        if (amount <= 0 && (companyAmount == null || companyAmount <= 0)) {
            return null;
        }
        return new Line(item.id(), item.name(), item.kind(), item.method(), amount, 0, 0, companyAmount, quantity,
                unitPrice, formula, item.sortOrder());
    }

    /** ⌊통상시급 × 배율 × 분 ÷ 60⌋ (6.3: ⌊15,311 × 1.5 × 600 ÷ 60⌋ = 229,665). */
    private static long attendanceAmount(long hourly, BigDecimal multiplier, int minutes) {
        return floor(BigDecimal.valueOf(hourly).multiply(multiplier).multiply(BigDecimal.valueOf(minutes))
                .divide(SIXTY, 4, RoundingMode.FLOOR));
    }

    /** 귀속 월 중 입사 · 퇴직이면 ⌊금액 × 재직 일수 ÷ 그달 일수⌋. */
    private static long prorate(long amount, Input in) {
        if (in.workedDays() >= in.monthDays()) {
            return amount;
        }
        return Math.floorDiv(amount * in.workedDays(), in.monthDays());
    }

    /** 과세표준이 속한 구간의 (과세표준 × 세율 ÷ 100 − 누진공제액), 0 미만이면 0. */
    private static long incomeTax(long base, List<Bracket> brackets) {
        for (Bracket b : brackets) {
            if (base >= b.lowerBound() && (b.upperBound() == null || base < b.upperBound())) {
                long tax = floor(BigDecimal.valueOf(base).multiply(b.rate()).divide(HUNDRED)
                        .subtract(BigDecimal.valueOf(b.progressiveDeduction())));
                return Math.max(0, tax);
            }
        }
        return 0;
    }

    private static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    /** "1.5시간 × 20,095원 × 1.5", "40분 × …", 결근은 "2일(16시간) × …". */
    static String formula(int minutes, int days, long hourly, BigDecimal multiplier) {
        String time = timeText(minutes);
        String quantity = days > 0 ? "%d일(%s)".formatted(days, time) : time;
        return "%s × %,d원 × %s".formatted(quantity, hourly, PayItemResponse.plain(multiplier).toPlainString());
    }

    static String timeText(int minutes) {
        if (minutes < 60) {
            return minutes + "분";
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + "시간";
        }
        if (minutes % 6 == 0) {
            return PayItemResponse.plain(BigDecimal.valueOf(minutes).divide(SIXTY, 1, RoundingMode.UNNECESSARY))
                    .toPlainString() + "시간";
        }
        return "%d시간 %d분".formatted(minutes / 60, minutes % 60);
    }

    private static long floor(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
