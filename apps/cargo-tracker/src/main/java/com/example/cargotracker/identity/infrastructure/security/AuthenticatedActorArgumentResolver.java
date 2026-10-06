package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * コントローラーの引数の {@link AuthenticatedActor} に、ログインした利用者を渡す（ADR-012）。
 * ほかのコンテキストのコントローラーは、注釈も Spring Security の型も使わずに操作者と所属企業を受け取る。
 * 未認証（ログインの画面など）では null を渡す。業務の画面は Spring Security が認証を求めるため null にならない。
 */
public class AuthenticatedActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedActor.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthenticatedActor resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return currentActor();
    }

    /** いまの request の認証された利用者。未認証なら null。 */
    public static AuthenticatedActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CargoUserDetails details
                ? details.actor()
                : null;
    }
}
