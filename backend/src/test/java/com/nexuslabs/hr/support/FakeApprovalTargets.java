package com.nexuslabs.hr.support;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 승인 엔진 테스트용 가짜 업무(@Import 한 테스트에서만 쓰인다). 신청 테이블 없이 메모리에 신청자·신청 시간을 두고
 * 확정·반려 콜백이 불렸는지 기록한다.
 *
 * <p>⚠️ B-08(휴가) · B-12(연장근무·출장·경비)에서 실제 ApprovalTarget 을 만들면 이 테스트 컨텍스트에서
 * "ApprovalTarget 이 두 개다" 로 실패한다. 그때 해당 종류를 아래 TYPES 에서 빼고 실제 신청 흐름으로 테스트한다.
 */
@TestConfiguration
public class FakeApprovalTargets {

    public static final List<ApprovalWorkType> TYPES = List.of(ApprovalWorkType.values());

    private final Map<ApprovalWorkType, Fake> fakes = new HashMap<>();

    @Bean
    public FakeApprovalTargets.Registry fakeRegistry() {
        return new Registry(fakes);
    }

    @Bean public ApprovalTarget fakeLeave() { return fake(ApprovalWorkType.LEAVE); }
    @Bean public ApprovalTarget fakeLeaveCancel() { return fake(ApprovalWorkType.LEAVE_CANCEL); }
    @Bean public ApprovalTarget fakeOvertime() { return fake(ApprovalWorkType.OVERTIME); }
    @Bean public ApprovalTarget fakeBusinessTrip() { return fake(ApprovalWorkType.BUSINESS_TRIP); }
    @Bean public ApprovalTarget fakeTripExpense() { return fake(ApprovalWorkType.TRIP_EXPENSE); }

    private Fake fake(ApprovalWorkType type) {
        return fakes.computeIfAbsent(type, Fake::new);
    }

    /** 테스트에서 가짜 신청을 만들고 콜백 기록을 본다. */
    public record Registry(Map<ApprovalWorkType, Fake> fakes) {

        public Fake of(ApprovalWorkType type) {
            return fakes.get(type);
        }

        public void reset() {
            fakes.values().forEach(Fake::reset);
        }
    }

    public static final class Fake implements ApprovalTarget {

        private final ApprovalWorkType type;
        private final Map<Long, long[]> requests = new HashMap<>(); // targetId -> {applicantId, requestedMinutes}
        public final List<Long> approved = new ArrayList<>();
        public final List<ApprovalStepView> lastSteps = new ArrayList<>();
        public final List<Long> rejected = new ArrayList<>();

        Fake(ApprovalWorkType type) {
            this.type = type;
        }

        public void request(long targetId, long applicantId, int requestedMinutes) {
            requests.put(targetId, new long[]{applicantId, requestedMinutes});
        }

        void reset() {
            requests.clear();
            approved.clear();
            lastSteps.clear();
            rejected.clear();
        }

        @Override
        public ApprovalWorkType type() {
            return type;
        }

        @Override
        public void onFinalApproved(long targetId, ApprovalStepView last) {
            approved.add(targetId);
            lastSteps.add(last);
        }

        @Override
        public void onRejected(long targetId) {
            rejected.add(targetId);
        }

        @Override
        public TargetSummary summary(long targetId) {
            long[] r = requests.get(targetId);
            return new TargetSummary(r[0], type + " #" + targetId, Map.of("targetId", targetId),
                    type == ApprovalWorkType.OVERTIME ? (int) r[1] : null);
        }
    }
}
