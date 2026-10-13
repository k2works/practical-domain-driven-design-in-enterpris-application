package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerification;
import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * A-02 の回復コードを確かめる（Bolt 31c、US-18 AC9 のログインの分、ADR-011）。照合と使用はアプリケーションのサービスに任せ、
 * 入口はその結果を認証の成否に直すだけにする（層の規則 D-5 の認証の例外）。通ったら TOTP と同じ結果（{@link TotpAuthenticationToken}、
 * 要素の権限 {@code FACTOR_TOTP}）を返す。password の要素の合わせ、成功の監査、session の直列化、業務の認可を TOTP と同じに保つ。
 */
public class RecoveryCodeAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RecoveryCodeAuthenticationProvider.class);

    private final RecoveryCodeVerificationService verificationService;

    public RecoveryCodeAuthenticationProvider(RecoveryCodeVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        RecoveryCodeAuthenticationToken input = (RecoveryCodeAuthenticationToken) authentication;
        if (!(input.getPrincipal() instanceof CargoUserDetails details)) {
            throw new InsufficientAuthenticationException("password の段の主体がない");
        }
        if (input.withTotpCode()) {
            // どちらを確かめたか曖昧にしない。照合せずに拒み、失敗のイベントで監査記録に残す（確認ポイント 7）
            throw new RecoveryCodeRejectedException();
        }
        String code = input.getCredentials() instanceof String given ? given : "";
        RecoveryCodeVerification verification;
        try {
            verification = verificationService.verify(
                    details.actor().userId(), details.actor().companyId(), code);
        } catch (IllegalArgumentException e) {
            // 保存したハッシュの形の誤りなど。500 にせず拒否し、運用で気付けるようにする（失敗へ倒す。T-42）
            LOG.error("回復コードを照合できず、拒否した: user={}", details.actor().userId().value(), e);
            throw new RecoveryCodeRejectedException();
        }
        if (!verification.accepted()) {
            throw new RecoveryCodeRejectedException();
        }
        return TotpAuthenticationToken.authenticated(
                details, FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return RecoveryCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
