package com.nexuslabs.hr.global.auth;

/** 토큰과 계정에서 확정한 지금 요청의 사용자. 권한은 담지 않는다(요청마다 DB에서 읽음). */
public record LoginUser(long employeeId, long companyId, long roleId, boolean mustChangePassword) {}
