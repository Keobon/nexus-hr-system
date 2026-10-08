package com.nexuslabs.hr.domain.payroll.service;

import com.nexuslabs.hr.domain.payroll.entity.AttendanceBasis;
import com.nexuslabs.hr.domain.payroll.entity.PayApplyTo;
import com.nexuslabs.hr.domain.payroll.entity.PayCalcMethod;
import com.nexuslabs.hr.domain.payroll.entity.PayItemKind;
import com.nexuslabs.hr.domain.payroll.service.PayrollCalculator.Bracket;
import com.nexuslabs.hr.domain.payroll.service.PayrollCalculator.Input;
import com.nexuslabs.hr.domain.payroll.service.PayrollCalculator.Item;
import com.nexuslabs.hr.domain.payroll.service.PayrollCalculator.Line;
import com.nexuslabs.hr.domain.payroll.service.PayrollCalculator.Result;
import com.nexuslabs.hr.domain.payroll.service.PayrollCalculator.Settings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 급여 계산(F-PAY-05). 첫 테스트는 백엔드 개발 안내 v2 6.3 "급여" 예제를 그대로 고정한다 —
 * 기본급 3,000,000 / 식대 200,000(비과세 한도 200,000, 통상임금) / 연장 600분 · 야간 120분 / 부양가족 1명 / 부록 B 기본값.
 */
class PayrollCalculatorTest {

    private static final List<Bracket> BRACKETS = List.of(
            new Bracket(0, 1_200_000L, bd("6"), 0),
            new Bracket(1_200_000, 4_200_000L, bd("15"), 108_000),
            new Bracket(4_200_000, 7_400_000L, bd("24"), 486_000),
            new Bracket(7_400_000, 12_500_000L, bd("35"), 1_300_000),
            new Bracket(12_500_000, null, bd("38"), 1_675_000));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Item rate(long id, String name, String employee, String company) {
        return new Item(id, name, PayItemKind.DEDUCTION, PayCalcMethod.TAXABLE_RATE, PayApplyTo.ALL, true, null, null,
                null, bd(employee), bd(company), null, null, null, null, false, 100 + (int) id);
    }

    private static Item attendance(long id, String name, PayItemKind kind, AttendanceBasis basis, String multiplier) {
        return new Item(id, name, kind, PayCalcMethod.ATTENDANCE, PayApplyTo.ALL, true, null, null, null, null, null,
                null, null, basis, bd(multiplier), false, 50 + (int) id);
    }

    /** 회사 등록 기본 항목(부록 B) + 식대. */
    private static List<Item> defaultItems() {
        List<Item> items = new ArrayList<>(List.of(
                rate(1, "국민연금", "4.5", "4.5"), rate(2, "건강보험", "3.545", "3.545"),
                rate(3, "장기요양보험", "0.4591", "0.4591"), rate(4, "고용보험", "0.9", "1.15"),
                attendance(5, "연장근로수당", PayItemKind.EARNING, AttendanceBasis.OVERTIME, "1.5"),
                attendance(6, "야간근로수당", PayItemKind.EARNING, AttendanceBasis.NIGHT, "0.5"),
                attendance(7, "휴일근로수당", PayItemKind.EARNING, AttendanceBasis.HOLIDAY, "1.5"),
                attendance(8, "휴일연장근로수당", PayItemKind.EARNING, AttendanceBasis.HOLIDAY_OVERTIME, "2.0"),
                attendance(9, "결근 공제", PayItemKind.DEDUCTION, AttendanceBasis.ABSENCE, "1.0"),
                new Item(10, "출장비 정산", PayItemKind.EARNING, PayCalcMethod.TRIP_EXPENSE, PayApplyTo.ALL, false, null,
                        null, null, null, null, null, null, null, null, false, 60),
                new Item(11, "식대", PayItemKind.EARNING, PayCalcMethod.FIXED, PayApplyTo.ALL, false, 200_000L, 200_000L,
                        null, null, null, null, null, null, null, true, 10)));
        return items;
    }

    private static Settings settings(List<Item> items) {
        return new Settings(items, bd("209"), 125_000, 0, bd("10"), BRACKETS);
    }

    private static Input input(long base, int worked, int monthDays, Map<AttendanceBasis, Integer> minutes,
                               int absentDays, long expense, int dependents, int children) {
        return new Input(base, worked, monthDays, Map.of(), Map.of(), minutes, absentDays, expense, dependents, children);
    }

    private static Line line(Result r, String name) {
        return r.lines().stream().filter(l -> l.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void 백엔드_안내_6_3_급여_예제() {
        Result r = PayrollCalculator.calculate(settings(defaultItems()), input(3_000_000, 31, 31,
                Map.of(AttendanceBasis.OVERTIME, 600, AttendanceBasis.NIGHT, 120), 0, 0, 1, 0));

        assertThat(r.ordinaryHourlyWage()).isEqualTo(15_311);
        assertThat(line(r, "연장근로수당").amount()).isEqualTo(229_665);
        assertThat(line(r, "야간근로수당").amount()).isEqualTo(15_311);
        assertThat(r.grossPay()).isEqualTo(3_444_976);
        assertThat(r.taxablePay()).isEqualTo(3_244_976);
        assertThat(line(r, "국민연금").amount()).isEqualTo(146_023);
        assertThat(line(r, "건강보험").amount()).isEqualTo(115_034);
        assertThat(line(r, "장기요양보험").amount()).isEqualTo(14_897);
        assertThat(line(r, "고용보험").amount()).isEqualTo(29_204);
        assertThat(line(r, "고용보험").companyAmount()).isEqualTo(37_317);
        assertThat(r.companyBurdenTotal()).isEqualTo(313_271);
        assertThat(r.incomeTax()).isEqualTo(359_996);
        assertThat(r.localIncomeTax()).isEqualTo(35_999);
        assertThat(r.totalDeduction()).isEqualTo(701_153);
        assertThat(r.netPay()).isEqualTo(2_743_823);
    }

    @Test
    void 근태_연동_줄은_시간_단가_산식을_남기고_0원_줄은_없다() {
        Result r = PayrollCalculator.calculate(settings(defaultItems()), input(3_000_000, 31, 31,
                Map.of(AttendanceBasis.OVERTIME, 90, AttendanceBasis.NIGHT, 40), 0, 0, 0, 0));

        Line overtime = line(r, "연장근로수당");
        assertThat(overtime.quantity()).isEqualByComparingTo("1.50");
        assertThat(overtime.unitPrice()).isEqualTo(22_966);                       // ⌊15,311 × 1.5⌋
        assertThat(overtime.formulaNote()).isEqualTo("1.5시간 × 15,311원 × 1.5");
        assertThat(line(r, "야간근로수당").formulaNote()).isEqualTo("40분 × 15,311원 × 0.5");
        assertThat(line(r, "야간근로수당").quantity()).isEqualByComparingTo("0.67");
        assertThat(r.lines()).extracting(Line::name)
                .doesNotContain("휴일근로수당", "휴일연장근로수당", "결근 공제", "출장비 정산");
        // 줄은 항목 정렬 순서대로(식대 10 → 근태 51… → 4대보험 101…)
        assertThat(r.lines()).extracting(Line::name).startsWith("식대", "연장근로수당", "야간근로수당", "국민연금");
    }

    @Test
    void 비과세는_한도까지만이고_넘는_금액은_과세액에_더한다() {
        List<Item> items = new ArrayList<>(defaultItems());
        items.removeIf(i -> i.id() == 11);
        items.add(new Item(11, "식대", PayItemKind.EARNING, PayCalcMethod.FIXED, PayApplyTo.ALL, false, 200_000L,
                250_000L, null, null, null, null, null, null, null, false, 10));
        Result r = PayrollCalculator.calculate(settings(items), input(3_000_000, 30, 30, Map.of(), 0, 120_000, 0, 0));

        assertThat(line(r, "식대").nonTaxableAmount()).isEqualTo(200_000);
        assertThat(line(r, "식대").taxableAmount()).isEqualTo(50_000);
        assertThat(line(r, "출장비 정산").nonTaxableAmount()).isEqualTo(120_000);     // 한도 없음 = 전액 비과세
        assertThat(r.grossPay()).isEqualTo(3_370_000);
        assertThat(r.taxablePay()).isEqualTo(3_050_000);
    }

    @Test
    void 입사_퇴직_달은_기본급_고정액_기본급_대비_퍼센트만_일할하고_통상시급은_일할_전_금액() {
        List<Item> items = new ArrayList<>(defaultItems());
        items.add(new Item(12, "직책수당", PayItemKind.EARNING, PayCalcMethod.BASE_RATE, PayApplyTo.ALL, true, null,
                null, bd("10"), null, null, null, null, null, null, true, 11));
        items.add(new Item(13, "성과급", PayItemKind.EARNING, PayCalcMethod.MANUAL, PayApplyTo.ALL, true, null, null,
                null, null, null, null, null, null, null, false, 12));
        Input in = new Input(3_100_000, 10, 31, Map.of(), Map.of(13L, 500_000L),
                Map.of(AttendanceBasis.OVERTIME, 60), 0, 0, 0, 0);
        Result r = PayrollCalculator.calculate(settings(items), in);

        assertThat(r.basePay()).isEqualTo(1_000_000);                              // ⌊3,100,000 × 10 ÷ 31⌋
        assertThat(line(r, "식대").amount()).isEqualTo(64_516);                     // ⌊200,000 × 10 ÷ 31⌋
        assertThat(line(r, "직책수당").amount()).isEqualTo(100_000);                // ⌊310,000 × 10 ÷ 31⌋
        assertThat(line(r, "성과급").amount()).isEqualTo(500_000);                  // 수동 입력은 일할 없음
        assertThat(r.ordinaryHourlyWage()).isEqualTo(17_272);                       // ⌊(3,100,000 + 식대 200,000 + 310,000) ÷ 209⌋
        assertThat(line(r, "연장근로수당").amount()).isEqualTo(25_908);             // 근태 연동도 일할 없음
    }

    @Test
    void 결근_공제와_다자녀_공제와_상하한() {
        List<Item> items = new ArrayList<>(defaultItems());
        items.removeIf(i -> i.id() == 1);
        items.add(new Item(1, "국민연금", PayItemKind.DEDUCTION, PayCalcMethod.TAXABLE_RATE, PayApplyTo.ALL, true, null,
                null, null, bd("4.5"), bd("4.5"), 2_000_000L, 390_000L, null, null, false, 101));
        Settings s = new Settings(items, bd("209"), 125_000, 20_000, bd("10"), BRACKETS);
        Result r = PayrollCalculator.calculate(s, input(3_000_000, 31, 31, Map.of(AttendanceBasis.ABSENCE, 960), 2, 0,
                3, 2));

        Line absence = line(r, "결근 공제");
        assertThat(absence.amount()).isEqualTo(244_976);                            // ⌊15,311 × 1.0 × 960 ÷ 60⌋
        assertThat(absence.formulaNote()).isEqualTo("2일(16시간) × 15,311원 × 1");
        assertThat(line(r, "국민연금").amount()).isEqualTo(90_000);                  // 상한 2,000,000 × 4.5%
        // 과세액 3,000,000(비과세 식대 제외) − 3 × 125,000 − (2 − 1) × 20,000 = 2,605,000 → × 15% − 108,000
        assertThat(r.taxablePay()).isEqualTo(3_000_000);
        assertThat(r.incomeTax()).isEqualTo(282_750);
    }

    @Test
    void 지정_직원_금액과_시간_표시() {
        List<Item> items = new ArrayList<>(defaultItems());
        items.add(new Item(14, "위험수당", PayItemKind.EARNING, PayCalcMethod.FIXED, PayApplyTo.SELECTED, true, null,
                null, null, null, null, null, null, null, null, false, 13));
        Input in = new Input(3_000_000, 31, 31, Map.of(14L, 150_000L), Map.of(), Map.of(), 0, 0, 0, 0);
        assertThat(line(PayrollCalculator.calculate(settings(items), in), "위험수당").amount()).isEqualTo(150_000);

        assertThat(PayrollCalculator.timeText(750)).isEqualTo("12.5시간");
        assertThat(PayrollCalculator.timeText(120)).isEqualTo("2시간");
        assertThat(PayrollCalculator.timeText(65)).isEqualTo("1시간 5분");
    }
}
