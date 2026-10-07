package com.nexuslabs.hr.domain.org.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 직급 · 직책 · 고용형태 등록·수정(API 설계서 5장) — 수정도 name · sortOrder 를 모두 보낸다.
 * isActive 가 null 이면 등록은 활성, 수정은 그대로.
 */
public record OrgSettingItemRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull Integer sortOrder,
        @JsonProperty("isActive") Boolean active) {
}
