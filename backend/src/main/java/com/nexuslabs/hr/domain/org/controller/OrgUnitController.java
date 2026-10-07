package com.nexuslabs.hr.domain.org.controller;

import com.nexuslabs.hr.domain.org.dto.OrgMemberResponse;
import com.nexuslabs.hr.domain.org.dto.OrgTreeNode;
import com.nexuslabs.hr.domain.org.dto.OrgUnitCreateRequest;
import com.nexuslabs.hr.domain.org.dto.OrgUnitMoveRequest;
import com.nexuslabs.hr.domain.org.dto.OrgUnitResponse;
import com.nexuslabs.hr.domain.org.service.OrgUnitService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 5장 — 조직 관리(F-ORG-01)와 조직도(F-ORG-02). */
@RestController
@RequestMapping("/api/org-units")
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    public OrgUnitController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    /** 조직도는 로그인만 하면 본다. includeInactive 는 ORG_MANAGE 가 있을 때만 적용된다. */
    @GetMapping("/tree")
    public ApiResponse<OrgTreeNode> tree(@CurrentUser LoginUser user,
                                         @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(orgUnitService.tree(user, includeInactive));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<OrgMemberResponse>> members(@CurrentUser LoginUser user, @PathVariable long id,
                                                        @RequestParam(defaultValue = "false") boolean includeSub) {
        return ApiResponse.ok(orgUnitService.members(user, id, includeSub));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgUnitResponse> create(@Valid @RequestBody OrgUnitCreateRequest request) {
        return ApiResponse.ok(orgUnitService.create(request));
    }

    /** 보낸 필드만 바꾼다. "안 보냄"과 "null(비움)"을 구별해야 해서 본문을 Map 으로 받는다. */
    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgUnitResponse> update(@CurrentUser LoginUser user, @PathVariable long id,
                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(orgUnitService.update(user, id, body));
    }

    @PostMapping("/{id}/move")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgUnitResponse> move(@CurrentUser LoginUser user, @PathVariable long id,
                                             @Valid @RequestBody OrgUnitMoveRequest request) {
        return ApiResponse.ok(orgUnitService.move(user, id, request));
    }

    @PostMapping("/{id}/deactivate")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgUnitResponse> deactivate(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(orgUnitService.deactivate(user, id));
    }

    @PostMapping("/{id}/activate")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<OrgUnitResponse> activate(@PathVariable long id) {
        return ApiResponse.ok(orgUnitService.activate(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(orgUnitService.delete(user, id));
    }
}
