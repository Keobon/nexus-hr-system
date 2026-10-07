package com.nexuslabs.hr;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 팀 규칙 검사(백엔드 개발 안내 v2 · ERD 설계서 v2 1장). 규칙을 어기는 코드가 PR 로 올라오면 CI 에서 여기가 실패한다.
 * 실패 메시지에 어긴 클래스·메서드 이름이 나온다. 규칙을 바꾸려면 문서를 먼저 고치고 이 파일을 고친다.
 */
@SpringBootTest
class ArchitectureRuleTest {

    private static final String BASE = "com.nexuslabs.hr";

    @Autowired JdbcTemplate jdbc;

    @Test
    void 규칙1_COMPANY를_뺀_모든_테이블에_company_id_NOT_NULL() {
        List<String> missing = jdbc.queryForList("""
                SELECT t.table_name FROM information_schema.tables t
                WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE' AND t.table_name <> 'company'
                  AND NOT EXISTS (SELECT 1 FROM information_schema.columns c
                                  WHERE c.table_schema = 'public' AND c.table_name = t.table_name
                                    AND c.column_name = 'company_id' AND c.is_nullable = 'NO')
                ORDER BY t.table_name
                """, String.class);
        assertThat(missing).as("company_id NOT NULL 컬럼이 없는 테이블 (회사 격리 규칙)").isEmpty();
    }

    @Test
    void 규칙2_COMPANY를_뺀_모든_엔티티는_TenantEntity_상속() {
        List<String> violations = new ArrayList<>();
        for (Class<?> entity : scan(Entity.class)) {
            if (!entity.getSimpleName().equals("Company") && !TenantEntity.class.isAssignableFrom(entity)) {
                violations.add(entity.getName());
            }
        }
        assertThat(violations).as("TenantEntity 를 상속하지 않은 엔티티 (@TenantId 회사 자동 필터가 안 걸린다)").isEmpty();
    }

    @Test
    void 규칙3_도메인_컨트롤러는_controller_패키지에_주소는_api로_시작() {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : scan(RestController.class)) {
            if (controller.getPackageName().startsWith(BASE + ".domain.")
                    && !controller.getPackageName().endsWith(".controller")) {
                violations.add(controller.getName() + " — controller 패키지가 아님");
            }
            String[] base = classPaths(controller);
            for (Method m : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : base) {
                    for (String path : paths) {
                        String full = prefix + path;
                        if (!full.startsWith("/api/")) {
                            violations.add(controller.getSimpleName() + "." + m.getName() + " — " + full);
                        }
                    }
                }
            }
        }
        assertThat(violations).as("규칙 3 위반").isEmpty();
    }

    @Test
    void 규칙4_도메인_서비스는_service_패키지에() {
        List<String> violations = scan(Service.class).stream()
                .filter(c -> c.getPackageName().startsWith(BASE + ".domain.") && !c.getPackageName().endsWith(".service"))
                .map(Class::getName)
                .toList();
        assertThat(violations).as("service 패키지 밖의 @Service").isEmpty();
    }

    @Test
    void 규칙5_컨트롤러는_DB에_직접_접근하지_않는다() {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : scan(RestController.class)) {
            for (Constructor<?> c : controller.getDeclaredConstructors()) {
                for (Class<?> param : c.getParameterTypes()) {
                    if (param == JdbcTemplate.class || param.getSimpleName().endsWith("Repository")) {
                        violations.add(controller.getSimpleName() + " ← " + param.getSimpleName());
                    }
                }
            }
        }
        assertThat(violations).as("컨트롤러는 서비스만 부른다 — DB 작업은 service 로 옮기기").isEmpty();
    }

    private static String[] classPaths(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        return mapping == null || mapping.path().length == 0 ? new String[]{""} : mapping.path();
    }

    private static Set<Class<?>> scan(Class<? extends Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        return scanner.findCandidateComponents(BASE).stream()
                .map(bd -> ClassUtils.resolveClassName(bd.getBeanClassName(), ArchitectureRuleTest.class.getClassLoader()))
                .collect(Collectors.toSet());
    }
}
