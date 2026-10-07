package com.nexuslabs.hr.domain.org.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.org.entity.OrgSettingItem;

/** 직급 · 직책 · 고용형태 한 건. */
public record OrgSettingItemResponse(long id, String name, int sortOrder, @JsonProperty("isActive") boolean active) {

    public static OrgSettingItemResponse from(OrgSettingItem item) {
        return new OrgSettingItemResponse(item.getId(), item.getName(), item.getSortOrder(), item.isActive());
    }
}
