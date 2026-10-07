package com.nexuslabs.hr.domain.account.service;

/** 로그인·회사 등록 뒤 프론트가 이동할 화면(F-AUTH-01). 위에서부터 판정한다. */
public enum LoginNext {
    CHANGE_PASSWORD, SETUP_WIZARD, HOME
}
