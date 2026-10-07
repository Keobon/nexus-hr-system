package com.nexuslabs.hr.domain.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 회사(테넌트). company_id 가 없는 유일한 테이블이라 공통 부모를 상속하지 않는다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String nameEn;
    private String businessRegNo;
    private String corpRegNo;
    private String ceoName;
    private String address;
    private String phone;
    private String fax;
    private String email;
    private String website;
    private String businessType;
    private String businessItem;
    private LocalDate foundedDate;
    private Long logoFileId;
    private short payDay = 25;
    private String employeeNoPrefix;
    private short fiscalYearStartMonth = 1;
    private boolean setupCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Company(String name, String businessRegNo, String ceoName, String address, String phone, String email) {
        this.name = name;
        this.businessRegNo = businessRegNo;
        this.ceoName = ceoName;
        this.address = address;
        this.phone = phone;
        this.email = email;
    }
}
