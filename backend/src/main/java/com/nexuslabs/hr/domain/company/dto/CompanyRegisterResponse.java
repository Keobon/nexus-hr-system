package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.account.service.LoginNext;

public record CompanyRegisterResponse(String token, long companyId, long employeeId, LoginNext next) {
}
