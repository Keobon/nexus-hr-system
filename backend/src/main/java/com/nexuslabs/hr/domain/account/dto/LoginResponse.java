package com.nexuslabs.hr.domain.account.dto;

import com.nexuslabs.hr.domain.account.service.LoginNext;

public record LoginResponse(String token, LoginNext next) {
}
