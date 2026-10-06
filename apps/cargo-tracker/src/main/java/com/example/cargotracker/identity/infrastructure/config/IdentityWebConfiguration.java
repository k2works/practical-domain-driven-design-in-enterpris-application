package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.infrastructure.security.AuthenticatedActorArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 認証された利用者をコントローラーの引数に渡す設定（ADR-012）。
 */
@Configuration(proxyBeanMethods = false)
public class IdentityWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedActorArgumentResolver());
    }
}
