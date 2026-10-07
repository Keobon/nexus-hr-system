package com.nexuslabs.hr.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 응답의 공통 래퍼(API 설계서 1.3).
 * 성공이면 data, 실패면 error가 채워진다. 빈 쪽은 응답에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorBody error) {
        return new ApiResponse<>(false, null, error);
    }
}
