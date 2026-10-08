package com.nexuslabs.hr.domain.attendance.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.payroll.entity.Paystub;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** 출장 경비 청구(BR-ATT-006). 합계는 저장하지 않고 줄 금액을 더한다. paystub 이 없으면 정산 미반영이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseClaim extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_trip_id")
    private BusinessTrip businessTrip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "request_status")
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paystub_id")
    private Paystub paystub;

    private String cancelReason;

    @OneToMany(mappedBy = "expenseClaim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ExpenseClaimLine> lines = new ArrayList<>();

    public ExpenseClaim(BusinessTrip businessTrip, Employee employee) {
        this.businessTrip = businessTrip;
        this.employee = employee;
    }

    public void addLine(ExpenseClaimLine line) {
        lines.add(line);
    }

    /** 최종 승인 → 정산 대기(paystub 없음). 다음 급여 정산이 명세서를 연결한다(BR-PAY-012). */
    public void approve() {
        requirePending();
        this.status = RequestStatus.APPROVED;
    }

    public void reject() {
        requirePending();
        this.status = RequestStatus.REJECTED;
    }

    /** 신청자 철회 · 퇴직 자동 취소. */
    public void cancel(String cancelReason) {
        requirePending();
        this.status = RequestStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    private void requirePending() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("승인대기가 아닌 경비 청구: " + id);
        }
    }
}
