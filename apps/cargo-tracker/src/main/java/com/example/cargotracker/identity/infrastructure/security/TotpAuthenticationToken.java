package com.example.cargotracker.identity.infrastructure.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * TOTP の要素の認証（Bolt 31、ADR-011）。認証されたら要素の権限 {@link #FACTOR_TOTP} を持つ。主体は password の段の主体
 * （{@link CargoUserDetails}）をそのまま引き継ぐ（引数の解決とほかのコンテキストは主体から {@code AuthenticatedActor} を得る）。
 * Spring Security 7 は、認証の結果の型が {@code toBuilder()} を宣言しているときだけ、先に認証した要素（password）の権限を
 * 結果に合わせる（{@code AbstractAuthenticationProcessingFilter} の mfaEnabled。Bolt 13 のスパイクで分かったこと）。
 */
public final class TotpAuthenticationToken extends AbstractAuthenticationToken {

    /** TOTP の要素の権限の名前。identity の外は参照しない（ArchUnit）。 */
    public static final String FACTOR_TOTP = "FACTOR_TOTP";

    @Serial
    private static final long serialVersionUID = 1L;

    /** session（Spring Session JDBC）に直列化して置く。主体は直列化できる {@link CargoUserDetails}（枠組みの型が Object）。 */
    @SuppressWarnings("java:S1948")
    private final Object principal;

    private String code;

    private TotpAuthenticationToken(Object principal, String code, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.code = code;
        setAuthenticated(!authorities.isEmpty());
    }

    private TotpAuthenticationToken(Builder builder) {
        super(builder);
        this.principal = builder.principal;
        this.code = builder.code;
    }

    /** 確かめる前のトークン。主体は password の段の主体。 */
    public static TotpAuthenticationToken unauthenticated(Object principal, String code) {
        return new TotpAuthenticationToken(principal, code, List.of());
    }

    /** 確かめた後のトークン。TOTP の要素の権限だけを持ち、password の要素の権限はフィルターが合わせる。 */
    public static TotpAuthenticationToken authenticated(Object principal, GrantedAuthority factor) {
        return new TotpAuthenticationToken(principal, null, List.of(factor));
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** 等しさは枠組みの規則（主体・権限・認証済みか）に従い、型も同じことを求める。 */
    @Override
    public boolean equals(Object other) {
        return other instanceof TotpAuthenticationToken && super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public Object getCredentials() {
        return code;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        code = null;
    }

    /** 先に認証した要素の権限を合わせるためのビルダー。 */
    public static final class Builder extends AbstractAuthenticationBuilder<Builder> {

        private Object principal;
        private String code;

        private Builder(TotpAuthenticationToken token) {
            super(token);
            this.principal = token.principal;
            this.code = token.code;
        }

        @Override
        public Builder principal(Object principal) {
            this.principal = principal;
            return this;
        }

        @Override
        public Builder credentials(Object credentials) {
            this.code = (String) credentials;
            return this;
        }

        @Override
        public TotpAuthenticationToken build() {
            return new TotpAuthenticationToken(this);
        }
    }
}
