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

    /** 회사명 · 사업자등록번호 · 대표자명 · 주소 — 바꿀 때마다 변경 이력을 남기는 네 항목(BR-TEN-004). */
    public void changeIdentity(String name, String businessRegNo, String ceoName, String address) {
        this.name = name;
        this.businessRegNo = businessRegNo;
        this.ceoName = ceoName;
        this.address = address;
    }

    public void changeContact(String phone, String fax, String email, String website) {
        this.phone = phone;
        this.fax = fax;
        this.email = email;
        this.website = website;
    }

    public void changeDetails(String nameEn, String corpRegNo, String businessType, String businessItem,
                              LocalDate foundedDate) {
        this.nameEn = nameEn;
        this.corpRegNo = corpRegNo;
        this.businessType = businessType;
        this.businessItem = businessItem;
        this.foundedDate = foundedDate;
    }

    public void changeLogo(Long logoFileId) {
        this.logoFileId = logoFileId;
    }

    /** 회계연도 시작월은 휴가가 부여된 뒤에는 바꿀 수 없다 — 서비스가 먼저 확인한다(F-COMP-02). */
    public void changeSettings(short payDay, String employeeNoPrefix, short fiscalYearStartMonth) {
        this.payDay = payDay;
        this.employeeNoPrefix = employeeNoPrefix;
        this.fiscalYearStartMonth = fiscalYearStartMonth;
    }
}
