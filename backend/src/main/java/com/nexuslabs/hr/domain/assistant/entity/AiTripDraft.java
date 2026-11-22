package com.nexuslabs.hr.domain.assistant.entity;

import com.nexuslabs.hr.domain.attendance.entity.TripType;
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

/** AI 출장 결재 초안(2026-11-19). 예상 비용은 말했을 때만(선택). 확인하면 그 출장 신청 ID 를 남긴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiTripDraft extends AiDraftBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "trip_type")
    private TripType tripType;

    private String destination;
    private String purpose;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long estimatedCost;
    private Long businessTripId;

    public AiTripDraft(long employeeId) {
        super(employeeId);
    }

    public void fill(String userMessage, TripType tripType, String destination, String purpose, LocalDate startDate,
                     LocalDate endDate, Long estimatedCost, OffsetDateTime expiresAt) {
        touch(userMessage, expiresAt);
        this.tripType = tripType;
        this.destination = destination;
        this.purpose = purpose;
        this.startDate = startDate;
        this.endDate = endDate;
        this.estimatedCost = estimatedCost;
    }

    @Override
    public boolean complete() {
        return tripType != null && destination != null && purpose != null && startDate != null && endDate != null;
    }

    public void confirm(long businessTripId) {
        markConfirmed();
        this.businessTripId = businessTripId;
    }
}
