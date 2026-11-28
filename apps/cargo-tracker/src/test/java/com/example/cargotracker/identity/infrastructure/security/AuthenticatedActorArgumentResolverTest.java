package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordVerifiedUser;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 認証された利用者の引数の解決（ADR-012）。業務の引数は、未認証なら例外にする（fail-closed。null を渡して 500 にしない）。
 * レイアウトのように未認証でも使う場所は Optional で受け取る（Bolt 14 レビュー）。{@code AuthenticatedActor} は 2 つの要素
 * （password と TOTP または回復コード）がそろった主体だけに渡し、password の段だけの主体は identity の中だけの
 * {@code PasswordVerifiedUser} で受ける（ADR-012 の見直しの 4、Bolt 31c）。
 */
class AuthenticatedActorArgumentResolverTest {

    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(Role.SHIPPER), "荷主 太郎", "荷主 A");
    private static final String EMAIL = "shipper@example.com";

    private final AuthenticatedActorArgumentResolver resolver = new AuthenticatedActorArgumentResolver();
    private final PasswordVerifiedUserArgumentResolver passwordStage = new PasswordVerifiedUserArgumentResolver();

    @SuppressWarnings("unused")
    void handler(
            AuthenticatedActor actor,
            Optional<AuthenticatedActor> optional,
            Optional<String> other,
            PasswordVerifiedUser user) {
        // 引数の型だけを使う見本のメソッド。呼ばない
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = AuthenticatedActorArgumentResolverTest.class.getDeclaredMethod(
                "handler", AuthenticatedActor.class, Optional.class, Optional.class, PasswordVerifiedUser.class);
        return new MethodParameter(method, index);
    }

    /** 主体に要素の権限を付けて認証済みにする。 */
    private static void signIn(String... factors) {
        CargoUserDetails details = new CargoUserDetails(ACTOR, EMAIL, true, null);
        List<GrantedAuthority> authorities = new ArrayList<>(details.getAuthorities());
        for (String factor : factors) {
            authorities.add(FactorGrantedAuthority.fromAuthority(factor));
        }
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details, null, authorities));
    }

    @AfterEach
    void 認証を消す() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 未認証なら業務の引数は例外にする() throws Exception {
        MethodParameter parameter = parameter(0);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void 未認証ならOptionalの引数は空にする() throws Exception {
        assertThat(resolver.resolveArgument(parameter(1), null, null, null)).isEqualTo(Optional.empty());
    }

    @Test
    void 認証された利用者のOptionalだけを受け持つ() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isTrue();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
        assertThat(resolver.supportsParameter(parameter(3))).isFalse();
    }

    @Test
    void 二つの要素がそろった主体には業務の引数を渡す() throws Exception {
        signIn(FactorGrantedAuthority.PASSWORD_AUTHORITY, TotpAuthenticationToken.FACTOR_TOTP);

        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(ACTOR);
        assertThat(resolver.resolveArgument(parameter(1), null, null, null)).isEqualTo(Optional.of(ACTOR));
    }

    @Test
    void passwordの段だけの主体には業務の引数を渡さずpasswordの段の主体を渡す() throws Exception {
        signIn(FactorGrantedAuthority.PASSWORD_AUTHORITY);
        MethodParameter actorParameter = parameter(0);

        assertThatThrownBy(() -> resolver.resolveArgument(actorParameter, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(resolver.resolveArgument(parameter(1), null, null, null)).isEqualTo(Optional.empty());
        assertThat(passwordStage.resolveArgument(parameter(3), null, null, null))
                .isEqualTo(new PasswordVerifiedUser(ACTOR.userId(), ACTOR.companyId(), EmailAddress.of(EMAIL)));
    }

    @Test
    void passwordの要素のない主体には業務の引数を渡さない() throws Exception {
        signIn(TotpAuthenticationToken.FACTOR_TOTP);
        MethodParameter actorParameter = parameter(0);

        assertThatThrownBy(() -> resolver.resolveArgument(actorParameter, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void 二つの要素がそろった主体にもpasswordの段の主体を渡す() throws Exception {
        signIn(FactorGrantedAuthority.PASSWORD_AUTHORITY, TotpAuthenticationToken.FACTOR_TOTP);

        assertThat(passwordStage.resolveArgument(parameter(3), null, null, null))
                .isEqualTo(new PasswordVerifiedUser(ACTOR.userId(), ACTOR.companyId(), EmailAddress.of(EMAIL)));
    }

    @Test
    void passwordの要素のない主体と未認証にはpasswordの段の主体を渡さない() throws Exception {
        MethodParameter parameter = parameter(3);

        assertThatThrownBy(() -> passwordStage.resolveArgument(parameter, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        signIn();
        assertThatThrownBy(() -> passwordStage.resolveArgument(parameter, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void passwordの段の主体の引数だけを受け持つ() throws Exception {
        assertThat(passwordStage.supportsParameter(parameter(3))).isTrue();
        assertThat(passwordStage.supportsParameter(parameter(0))).isFalse();
    }
}
