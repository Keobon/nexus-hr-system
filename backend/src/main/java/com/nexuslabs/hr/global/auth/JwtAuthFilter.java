package com.nexuslabs.hr.global.auth;

import com.nexuslabs.hr.global.error.ErrorCode;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.ErrorBody;
import com.nexuslabs.hr.global.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * /api/** 요청의 토큰을 검증하고 LoginUser와 회사(TenantContext)를 정한다(백엔드 안내 3.2).
 * 필터에서 난 오류는 @RestControllerAdvice가 잡지 못하므로 여기서 직접 응답을 쓴다.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String LOGIN_USER_ATTR = "hr.loginUser";

    /** 토큰 없이 부르는 엔드포인트(메서드 + 경로). */
    private static final Set<String> PUBLIC = Set.of("POST /api/companies", "POST /api/auth/login");

    /** 비밀번호 변경이 필요한 계정도 부를 수 있는 엔드포인트. */
    private static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE =
            Set.of("PATCH /api/auth/password", "POST /api/auth/logout", "GET /api/me");

    private final JwtProvider jwtProvider;
    private final AuthAccountReader accountReader;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtProvider jwtProvider, AuthAccountReader accountReader, ObjectMapper objectMapper) {
        this.jwtProvider = jwtProvider;
        this.accountReader = accountReader;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !path.startsWith("/api/") || PUBLIC.contains(request.getMethod() + " " + path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, ErrorCode.AUTH_REQUIRED);
            return;
        }
        JwtProvider.Result result = jwtProvider.parse(header.substring(7));
        if (result instanceof JwtProvider.Expired) {
            writeError(response, ErrorCode.AUTH_TOKEN_EXPIRED);
            return;
        }
        if (!(result instanceof JwtProvider.Valid valid)) {
            writeError(response, ErrorCode.AUTH_REQUIRED);
            return;
        }
        Optional<AuthAccountReader.AccountState> account = accountReader.find(valid.employeeId(), valid.companyId());
        if (account.isEmpty() || !account.get().usable()) {
            writeError(response, ErrorCode.AUTH_ACCOUNT_INACTIVE);
            return;
        }
        AuthAccountReader.AccountState state = account.get();
        if (state.mustChangePassword()
                && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(request.getMethod() + " " + path(request))) {
            writeError(response, ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED);
            return;
        }

        request.setAttribute(LOGIN_USER_ATTR,
                new LoginUser(valid.employeeId(), valid.companyId(), state.roleId(), state.mustChangePassword()));
        TenantContext.set(valid.companyId());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(ErrorBody.of(code.name(), code.defaultMessage())));
    }
}
