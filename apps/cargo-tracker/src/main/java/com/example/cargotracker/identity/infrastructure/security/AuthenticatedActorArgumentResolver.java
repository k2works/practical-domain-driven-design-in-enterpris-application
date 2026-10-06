package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * コントローラーの引数の {@link AuthenticatedActor} に、ログインした利用者を渡す（ADR-012）。
 * ほかのコンテキストのコントローラーは、注釈も Spring Security の型も使わずに操作者と所属企業を受け取る。
 * 業務の引数（{@code AuthenticatedActor}）は、未認証なら例外にして A-01 へ移す（fail-closed。null を渡さない）。
 * 未認証の画面でも使う場所（レイアウトのヘッダー）は {@code Optional<AuthenticatedActor>} で受け取る（Bolt 14 レビュー）。
 * 引数の解決があることで、{@code AuthenticatedActor} が request の値から組み立てられることもない。
 */
public class AuthenticatedActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedActor.class.equals(parameter.getParameterType()) || isOptionalActor(parameter);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Optional<AuthenticatedActor> actor = currentActor();
        if (isOptionalActor(parameter)) {
            return actor;
        }
        return actor.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("ログインしていない"));
    }

    private static boolean isOptionalActor(MethodParameter parameter) {
        return Optional.class.equals(parameter.getParameterType())
                && AuthenticatedActor.class.equals(parameter.nested().getNestedParameterType());
    }

    private static Optional<AuthenticatedActor> currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CargoUserDetails details
                ? Optional.of(details.actor())
                : Optional.empty();
    }
}
