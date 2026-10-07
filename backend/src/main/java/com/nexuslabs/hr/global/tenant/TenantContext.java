package com.nexuslabs.hr.global.tenant;

/**
 * 지금 요청의 회사 ID. JwtAuthFilter가 넣고 요청이 끝나면 비운다.
 * 회사 등록·로그인처럼 토큰이 없는 흐름은 회사를 확정한 뒤 직접 set 한다.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long companyId) { CURRENT.set(companyId); }

    public static Long get() { return CURRENT.get(); }

    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("회사(테넌트)가 정해지지 않은 상태입니다");
        }
        return id;
    }

    public static void clear() { CURRENT.remove(); }
}
