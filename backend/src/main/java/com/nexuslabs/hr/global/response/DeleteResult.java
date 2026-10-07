package com.nexuslabs.hr.global.response;

/**
 * 설정 항목 삭제 API의 응답(API 설계서 1.7, BR-ORG-001).
 * 쓰인 적 없으면 실제로 지우고 DELETED, 쓰였으면 비활성화하고 DEACTIVATED.
 */
public record DeleteResult(Result result) {

    public enum Result { DELETED, DEACTIVATED }

    public static DeleteResult deleted() {
        return new DeleteResult(Result.DELETED);
    }

    public static DeleteResult deactivated() {
        return new DeleteResult(Result.DEACTIVATED);
    }
}
