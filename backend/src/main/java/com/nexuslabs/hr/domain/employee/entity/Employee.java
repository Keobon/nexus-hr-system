package com.nexuslabs.hr.domain.employee.entity;

import com.nexuslabs.hr.domain.org.entity.EmploymentType;
import com.nexuslabs.hr.domain.org.entity.JobGrade;
import com.nexuslabs.hr.domain.org.entity.JobTitle;
import com.nexuslabs.hr.domain.org.entity.OrgUnit;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** 직원. 물리 삭제가 없고(BR-EMP-002) 소속·직급·직책은 발령으로만 바뀐다(BR-EMP-001). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeNo;
    private String name;
    private String nameEn;
    private String email;
    private String phone;
    private String address;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "gender")
    private Gender gender;

    private String emergencyName;
    private String emergencyRelation;
    private String emergencyPhone;
    private LocalDate hireDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_grade_id")
    private JobGrade jobGrade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employment_type_id")
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "emp_status")
    private EmpStatus status = EmpStatus.ACTIVE;

    private boolean payrollEligible = true;
    private LocalDate contractEndDate;
    private LocalDate probationEndDate;
    private Long profileFileId;
    private String hrMemo;
    private String bankName;
    private String bankAccountEnc;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 4)
    private String bankAccountLast4;

    private String bankAccountHolder;

    public Employee(String employeeNo, String name, String email, LocalDate hireDate, OrgUnit orgUnit,
                    JobGrade jobGrade, JobTitle jobTitle, EmploymentType employmentType) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.email = email;
        this.hireDate = hireDate;
        this.orgUnit = orgUnit;
        this.jobGrade = jobGrade;
        this.jobTitle = jobTitle;
        this.employmentType = employmentType;
    }

    public void changeContact(String phone, String address) {
        this.phone = phone;
        this.address = address;
    }

    public void changePersonal(String nameEn, LocalDate birthDate, Gender gender) {
        this.nameEn = nameEn;
        this.birthDate = birthDate;
        this.gender = gender;
    }

    public void changeEmergencyContact(String name, String relation, String phone) {
        this.emergencyName = name;
        this.emergencyRelation = relation;
        this.emergencyPhone = phone;
    }

    public void changeContractDates(LocalDate contractEndDate, LocalDate probationEndDate) {
        this.contractEndDate = contractEndDate;
        this.probationEndDate = probationEndDate;
    }

    public void changeProfileFile(Long profileFileId) {
        this.profileFileId = profileFileId;
    }

    public void changePayrollEligible(boolean payrollEligible) {
        this.payrollEligible = payrollEligible;
    }

    /** 인사 메모는 EMPLOYEE_MANAGE 에게만 보인다. 본인 응답에는 넣지 않는다. */
    public void changeHrMemo(String hrMemo) {
        this.hrMemo = hrMemo;
    }

    /** 급여 계좌(F-PAY-03). 계좌번호는 암호문과 뒤 4자리만 받는다 — 원문은 엔티티에 두지 않는다. */
    public void changeBankAccount(String bankName, String accountEnc, String accountLast4, String holder) {
        this.bankName = bankName;
        this.bankAccountEnc = accountEnc;
        this.bankAccountLast4 = accountLast4;
        this.bankAccountHolder = holder;
    }

    /** 재직상태 변경(F-EMP-04). 이력 추가와 퇴직 연쇄 처리는 EmploymentStatusService 가 한다. */
    public void changeStatus(EmpStatus status) {
        this.status = status;
    }
}
