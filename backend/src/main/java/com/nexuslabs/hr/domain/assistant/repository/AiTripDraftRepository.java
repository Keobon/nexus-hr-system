package com.nexuslabs.hr.domain.assistant.repository;

import com.nexuslabs.hr.domain.assistant.entity.AiTripDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiTripDraftRepository extends JpaRepository<AiTripDraft, Long> {

    /** 확인 · 폐기 · 수정이 같은 초안에 동시에 오면 한 건씩. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM AiTripDraft d WHERE d.id = :id")
    Optional<AiTripDraft> findForUpdate(@Param("id") long id);
}
