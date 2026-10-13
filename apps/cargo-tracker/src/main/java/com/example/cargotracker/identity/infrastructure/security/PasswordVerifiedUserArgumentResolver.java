package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordVerifiedUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * password の段だけで開く画面（A-02・A-07）の引数の {@link PasswordVerifiedUser} に、password を確かめた利用者を渡す（Bolt 31c、
 * ADR-012 の見直しの 4）。password の要素（{@code FACTOR_PASSWORD}）を持つ主体なら渡し（2 つの要素がそろった主体を含む）、
 * そうでなければ例外にして A-01 へ移す（fail-closed。null を渡さない）。
 */
public class PasswordVerifiedUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PasswordVerifiedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CargoUserDetails details
                && AuthenticatedActorArgumentResolver.hasFactor(
                        authentication, FactorGrantedAuthority.PASSWORD_AUTHORITY)) {
            return new PasswordVerifiedUser(
                    details.actor().userId(), details.actor().companyId(), EmailAddress.of(details.getUsername()));
        }
        throw new AuthenticationCredentialsNotFoundException("password を確かめていない");
    }
}
