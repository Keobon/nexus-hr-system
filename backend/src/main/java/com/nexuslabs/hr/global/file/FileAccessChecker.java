package com.nexuslabs.hr.global.file;

import com.nexuslabs.hr.global.auth.LoginUser;

import java.util.Optional;

/**
 * 파일 내려받기 권한 판단(API 설계서 14장). FILE 테이블에는 용도가 없으므로 그 파일을 참조하는 쪽이 판단한다.
 * 파일을 참조하는 도메인(경비 영수증 · 회사 서류 · 직원 서류 …)은 이 인터페이스를 구현한 @Component 를 하나 만든다.
 *
 * <p>반환값: 내 도메인이 참조하지 않는 파일이면 empty, 참조하면 허용 여부.
 * 어느 checker도 참조하지 않는 파일은 올린 사람만 내려받을 수 있다.
 * 같은 파일을 여러 곳이 참조하면 하나라도 허용하면 허용한다.
 */
public interface FileAccessChecker {

    Optional<Boolean> canRead(LoginUser user, long fileId);
}
