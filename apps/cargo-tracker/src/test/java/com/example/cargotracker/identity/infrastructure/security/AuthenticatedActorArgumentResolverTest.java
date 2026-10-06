package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 認証された利用者の引数の解決（ADR-012）。業務の引数は、未認証なら例外にする（fail-closed。null を渡して 500 にしない）。
 * レイアウトのように未認証でも使う場所は Optional で受け取る（Bolt 14 レビュー）。
 */
class AuthenticatedActorArgumentResolverTest {

    private final AuthenticatedActorArgumentResolver resolver = new AuthenticatedActorArgumentResolver();

    @SuppressWarnings("unused")
    void handler(AuthenticatedActor actor, Optional<AuthenticatedActor> optional, Optional<String> other) {}

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = AuthenticatedActorArgumentResolverTest.class.getDeclaredMethod(
                "handler", AuthenticatedActor.class, Optional.class, Optional.class);
        return new MethodParameter(method, index);
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
    }
}
