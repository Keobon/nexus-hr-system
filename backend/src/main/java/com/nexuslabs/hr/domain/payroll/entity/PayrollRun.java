package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 월 급여 정산(BR-PAY-005). 귀속 월마다 살아 있는 정산은 1행이고 삭제하지 않는다. 확정 뒤에는 상태 · 지급 · 취소 정보만 바뀐다.
 * 확정 → 지급완료, 또는 지급 전이면 확정 → 취소(2026-11-05). 취소된 정산은 기록으로 남고 같은 달을 다시 확정한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollRun extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String payMonth;

    private LocalDate payDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "payroll_status")
    private PayrollStatus status = PayrollStatus.CONFIRMED;

    private String companyNameSnap;
    private String ceoNameSnap;
    private String businessRegNoSnap;
    private String companyAddressSnap;
    private long confirmedBy;
    private OffsetDateTime confirmedAt;
    private Long paidBy;
    private OffsetDateTime paidAt;
    private Long cancelledBy;
    private OffsetDateTime cancelledAt;
    private String cancelReason;

    public PayrollRun(String payMonth, LocalDate payDate, String companyNameSnap, String ceoNameSnap,
                      String businessRegNoSnap, String companyAddressSnap, long confirmedBy,
                      OffsetDateTime confirmedAt) {
        this.payMonth = payMonth;
        this.payDate = payDate;
        this.companyNameSnap = companyNameSnap;
        this.ceoNameSnap = ceoNameSnap;
        this.businessRegNoSnap = businessRegNoSnap;
        this.companyAddressSnap = companyAddressSnap;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    /** 확정 → 지급완료 한 방향뿐(BR-PAY-015). 명세서는 바뀌지 않는다. */
    public void markPaid(long paidBy, OffsetDateTime paidAt) {
        if (status != PayrollStatus.CONFIRMED) {
            throw new IllegalStateException("확정 상태가 아닌 정산: " + id);
        }
        this.status = PayrollStatus.PAID;
        this.paidBy = paidBy;
        this.paidAt = paidAt;
    }

    /** 확정 → 취소(지급완료 전만). 명세서는 그대로 남는다(append-only) — 조회 · 판단에서 빠진다. */
    public void cancel(long cancelledBy, OffsetDateTime cancelledAt, String reason) {
        if (status != PayrollStatus.CONFIRMED) {
            throw new IllegalStateException("확정 상태가 아닌 정산: " + id);
        }
        this.status = PayrollStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
        this.cancelReason = reason;
    }
}
