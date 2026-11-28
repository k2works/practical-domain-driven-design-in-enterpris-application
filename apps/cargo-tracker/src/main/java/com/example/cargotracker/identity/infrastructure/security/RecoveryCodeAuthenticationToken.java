package com.example.cargotracker.identity.infrastructure.security;

import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * A-02 で入れた回復コードの、確かめる前の入力（Bolt 31c、US-18 AC9 のログインの分、ADR-011）。主体は password の段の主体。
 * 確かめた後の結果は TOTP と同じ {@link TotpAuthenticationToken}（要素の権限 {@code FACTOR_TOTP}）にするので、この型は認証の
 * 結果として session に置かれない。平文は request の範囲でしか持たない（ProviderManager が平文を消すのは認証の結果のトークン
 * だけで、この入力には呼ばない。失敗のイベントに入るので、リスナーは {@code getCredentials()} を読まない）。回復コードと認証コードの
 * 両方を送った入力は、照合せずに拒むために印を付ける。
 */
public final class RecoveryCodeAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主体は直列化できる {@link CargoUserDetails}（枠組みの型が Object）。 */
    @SuppressWarnings("java:S1948")
    private final Object principal;

    private String recoveryCode;

    private final boolean withTotpCode;

    private RecoveryCodeAuthenticationToken(Object principal, String recoveryCode, boolean withTotpCode) {
        super(List.of());
        this.principal = principal;
        this.recoveryCode = recoveryCode;
        this.withTotpCode = withTotpCode;
        setAuthenticated(false);
    }

    /**
     * 確かめる前の入力。
     *
     * @param withTotpCode 認証コードも一緒に送った（どちらを確かめたか曖昧にしないため拒む）
     */
    public static RecoveryCodeAuthenticationToken unauthenticated(
            Object principal, String recoveryCode, boolean withTotpCode) {
        return new RecoveryCodeAuthenticationToken(principal, recoveryCode, withTotpCode);
    }

    /** 認証コードも一緒に送った入力か。 */
    public boolean withTotpCode() {
        return withTotpCode;
    }

    @Override
    public Object getCredentials() {
        return recoveryCode;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    /** 回復コードの平文を消す（照合の後に残さない）。 */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        recoveryCode = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecoveryCodeAuthenticationToken && super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
