package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.TotpVerification;
import com.example.cargotracker.identity.application.internal.commandservices.TotpVerificationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * TOTP の要素を確かめる（Bolt 31、ADR-011）。照合と刻みの使用はアプリケーションのサービスに任せ、結果を認証の成否に直すだけにする
 * （層の規則 D-5 の認証の例外。ADR-012）。拒否は {@link TotpRejectedException} にし、password の誤りと同じ認証の失敗のイベントで
 * 監査記録に書く。password の要素と合わせるのはフィルター（mfaEnabled）が行う。
 */
public class TotpAuthenticationProvider implements AuthenticationProvider {

    private final TotpVerificationService verificationService;

    public TotpAuthenticationProvider(TotpVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof CargoUserDetails details)) {
            throw new InsufficientAuthenticationException("password の段の主体がない");
        }
        String code = authentication.getCredentials() instanceof String given ? given : "";
        TotpVerification verification =
                verificationService.verify(details.actor().userId(), code);
        if (!verification.accepted()) {
            throw new TotpRejectedException(verification);
        }
        return TotpAuthenticationToken.authenticated(
                details, FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return TotpAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
