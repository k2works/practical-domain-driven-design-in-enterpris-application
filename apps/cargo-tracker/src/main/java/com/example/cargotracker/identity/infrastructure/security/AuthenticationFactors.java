package com.example.cargotracker.identity.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * 認証の要素の権限の判定（Bolt 31c の開発レビュー）。password の段の主体か、2 つの要素（password と TOTP または回復コード）が
 * そろった主体かを、引数の解決と認証の入口で同じ規則で見る。
 */
final class AuthenticationFactors {

    private AuthenticationFactors() {}

    /** 認証された主体が要素の権限を持つか。 */
    static boolean has(Authentication authentication, String factor) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> factor.equals(authority.getAuthority()));
    }

    /** password を確かめた主体か（2 つの要素がそろった主体を含む）。 */
    static boolean passwordVerified(Authentication authentication) {
        return has(authentication, FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }

    /** 2 つの要素がそろった主体か。 */
    static boolean fullyAuthenticated(Authentication authentication) {
        return passwordVerified(authentication) && has(authentication, TotpAuthenticationToken.FACTOR_TOTP);
    }
}
