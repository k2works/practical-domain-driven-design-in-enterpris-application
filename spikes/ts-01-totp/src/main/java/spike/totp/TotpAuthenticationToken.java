package spike.totp;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * TOTP（または回復コード）の要素の認証。認証されたら要素の権限 FACTOR_TOTP を持つ。
 * Spring Security 7 は、認証の結果の型が {@code toBuilder()} を宣言しているときだけ、先に認証した要素（password）の権限を
 * 結果に合わせる（AbstractAuthenticationProcessingFilter の mfaEnabled）。そのためビルダーを持つ（スパイクで分かったこと）。
 */
public final class TotpAuthenticationToken extends AbstractAuthenticationToken {

    public static final String FACTOR_TOTP = "FACTOR_TOTP";

    private final String username;
    private final String code;

    private TotpAuthenticationToken(String username, String code, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.username = username;
        this.code = code;
        setAuthenticated(!authorities.isEmpty());
    }

    private TotpAuthenticationToken(Builder builder) {
        super(builder);
        this.username = builder.username;
        this.code = builder.code;
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static TotpAuthenticationToken unauthenticated(String username, String code) {
        return new TotpAuthenticationToken(username, code, List.of());
    }

    public static TotpAuthenticationToken authenticated(String username, GrantedAuthority factor) {
        return new TotpAuthenticationToken(username, null, List.of(factor));
    }

    @Override
    public Object getCredentials() {
        return code;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    /** 先に認証した要素の権限を合わせるためのビルダー。 */
    public static final class Builder extends AbstractAuthenticationBuilder<Builder> {

        private String username;
        private String code;

        private Builder(TotpAuthenticationToken token) {
            super(token);
            this.username = token.username;
            this.code = token.code;
        }

        @Override
        public Builder principal(Object principal) {
            this.username = String.valueOf(principal);
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
