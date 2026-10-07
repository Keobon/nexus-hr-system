package com.nexuslabs.hr.domain.leave;

import com.nexuslabs.hr.domain.leave.service.LeaveCalculator;
import com.nexuslabs.hr.domain.leave.service.LeaveTypeRule;
import com.nexuslabs.hr.domain.leave.service.LeaveYear;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 휴가 연도 · 근속 가산 · 첫해 비례(F-LEAVE-01·02). DB 없이 계산만. */
class LeaveCalculatorTest {

    /** 회사 등록 기본값 연차: 15일, 첫해 비례, 근속 3년부터 2년마다 1일, 최대 25일. */
    static final LeaveTypeRule ANNUAL = new LeaveTypeRule(15, true, 3, 2, 1, 25);
    /** 근속 가산·비례 없는 종류(seed 의 리프레시휴가 5일). */
    static final LeaveTypeRule REFRESH = new LeaveTypeRule(5, false, null, null, null, null);

    static final LeaveYear Y2026 = LeaveYear.of(2026, 1);

    @Test
    void 휴가_연도는_회계연도_시작월부터_12개월() {
        assertThat(Y2026.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(Y2026.end()).isEqualTo(LocalDate.of(2026, 12, 31));

        LeaveYear april = LeaveYear.containing(LocalDate.of(2026, 3, 15), 4);
        assertThat(april.year()).isEqualTo(2025);
        assertThat(april.start()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(april.end()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(LeaveYear.containing(LocalDate.of(2026, 4, 1), 4).year()).isEqualTo(2026);
    }

    @Test
    void 근속_가산은_시작_근속연수부터_간격마다() {
        LocalDate start = Y2026.start();
        assertThat(LeaveCalculator.regularDays(ANNUAL, LocalDate.of(2024, 3, 1), start)).isEqualTo(15); // 1년
        assertThat(LeaveCalculator.regularDays(ANNUAL, LocalDate.of(2023, 1, 1), start)).isEqualTo(16); // 3년 → +1
        assertThat(LeaveCalculator.regularDays(ANNUAL, LocalDate.of(2022, 6, 1), start)).isEqualTo(16); // 3년 7개월
        assertThat(LeaveCalculator.regularDays(ANNUAL, LocalDate.of(2022, 1, 1), start)).isEqualTo(16); // 4년
        assertThat(LeaveCalculator.regularDays(ANNUAL, LocalDate.of(2021, 1, 1), start)).isEqualTo(17); // 5년 → +2
        assertThat(LeaveCalculator.regularDays(ANNUAL, LocalDate.of(2000, 1, 1), start)).isEqualTo(25); // 최대
        assertThat(LeaveCalculator.regularDays(REFRESH, LocalDate.of(2000, 1, 1), start)).isEqualTo(5);
    }

    @Test
    void 입사_부여는_남은_개월_비례_내림() {
        // 3월 입사 → 3~12월 10개월 → 15 × 10/12 = 12.5 → 12 (seed_demo 넥서스랩스 9번 직원)
        assertThat(LeaveCalculator.hireDays(ANNUAL, LocalDate.of(2026, 3, 2), Y2026)).isEqualTo(12);
        // 7월 입사 → 6개월 → 7.5 → 7 (seed_demo 2번 회사 109번 직원)
        assertThat(LeaveCalculator.hireDays(ANNUAL, LocalDate.of(2026, 7, 1), Y2026)).isEqualTo(7);
        assertThat(LeaveCalculator.hireDays(ANNUAL, LocalDate.of(2026, 12, 15), Y2026)).isEqualTo(1);
        // 비례 아닌 종류는 연간 부여일수 그대로
        assertThat(LeaveCalculator.hireDays(REFRESH, LocalDate.of(2026, 7, 1), Y2026)).isEqualTo(5);
    }

    @Test
    void 연도_시작_전_입사자를_나중에_등록하면_정기_부여와_같다() {
        assertThat(LeaveCalculator.hireDays(ANNUAL, LocalDate.of(2025, 5, 1), Y2026)).isEqualTo(15);
        assertThat(LeaveCalculator.hireDays(ANNUAL, LocalDate.of(2021, 1, 1), Y2026)).isEqualTo(17);
    }

    @Test
    void 회계연도가_4월_시작이면_연도_말은_3월() {
        LeaveYear fy = LeaveYear.of(2026, 4);
        // 2027-01 입사 → 1~3월 3개월 → 15 × 3/12 = 3.75 → 3
        assertThat(LeaveCalculator.hireDays(ANNUAL, LocalDate.of(2027, 1, 10), fy)).isEqualTo(3);
    }
}
