package com.nexuslabs.hr.global.config;

import com.nexuslabs.hr.global.auth.CurrentUserArgumentResolver;
import com.nexuslabs.hr.global.permission.PermissionInterceptor;
import com.nexuslabs.hr.global.permission.PermissionReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PermissionReader permissionReader;

    public WebConfig(PermissionReader permissionReader) {
        this.permissionReader = permissionReader;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionInterceptor(permissionReader)).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
