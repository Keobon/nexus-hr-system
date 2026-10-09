package com.nexuslabs.hr.domain.attendance.repository;

import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {

    /** 승인 목록 요약용 — 청구 여러 건을 출장 · 경비 줄과 함께 쿼리 한 번으로 읽는다. */
    @Query("""
            SELECT DISTINCT c FROM ExpenseClaim c
            JOIN FETCH c.businessTrip LEFT JOIN FETCH c.lines
            WHERE c.id IN :ids""")
    List<ExpenseClaim> findAllWithTripAndLines(@Param("ids") Collection<Long> ids);
}
