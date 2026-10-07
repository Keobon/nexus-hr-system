package com.nexuslabs.hr.domain.account.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 계정. 직원 1명에 1개이고 로그인 ID는 직원 이메일이다(BR-AUTH-005). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "is_active")
    private boolean active = true;

    private boolean mustChangePassword = true;
    private short failedLoginCount = 0;
    private OffsetDateTime lockedUntil;
    private OffsetDateTime lastLoginAt;

    public Account(Employee employee, String passwordHash, Role role) {
        this.employee = employee;
        this.passwordHash = passwordHash;
        this.role = role;
    }
}
