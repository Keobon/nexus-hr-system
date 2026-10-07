package com.nexuslabs.hr.domain.account.dto;

/** 임시 비밀번호. 이 응답에서 한 번만 보여 주고 서버에는 해시만 남는다. */
public record TemporaryPasswordResponse(String temporaryPassword) {
}
