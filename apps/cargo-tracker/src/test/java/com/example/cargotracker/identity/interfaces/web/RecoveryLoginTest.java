package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 回復コードでログインした印（Bolt 33b）。印の利用者が今の主体で、ログインから 15 分の間だけ A-08 で登録し直せる。 */
class RecoveryLoginTest {

    private static final Instant LOGGED_IN = Instant.parse("2026-10-15T01:00:00Z");

    private final AuthenticatedActor actor = actor();

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(Role.SHIPPER), "荷主 太郎", "荷主 A");
    }

    private MockHttpSession marked(AuthenticatedActor owner) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                SessionKeys.RECOVERY_LOGIN_USER, owner.userId().value().toString());
        session.setAttribute(SessionKeys.RECOVERY_LOGIN_AT, LOGGED_IN);
        return session;
    }

    @Test
    void 印の利用者が今の主体でログインから15分の間だけ登録し直せる() {
        MockHttpSession session = marked(actor);

        assertThat(RecoveryLogin.isMarked(
                        session, actor, LOGGED_IN.plus(Duration.ofMinutes(15)).minusSeconds(1)))
                .isTrue();
        assertThat(RecoveryLogin.stateOf(session, actor, LOGGED_IN.plus(Duration.ofMinutes(15))))
                .as("ちょうど 15 分")
                .isEqualTo(RecoveryLogin.State.EXPIRED);
    }

    @Test
    void 別の利用者の印と印のないsessionは印として扱わない() {
        assertThat(RecoveryLogin.stateOf(marked(actor()), actor, LOGGED_IN)).isEqualTo(RecoveryLogin.State.NONE);
        assertThat(RecoveryLogin.stateOf(new MockHttpSession(), actor, LOGGED_IN))
                .isEqualTo(RecoveryLogin.State.NONE);
        assertThat(RecoveryLogin.stateOf(null, actor, LOGGED_IN)).isEqualTo(RecoveryLogin.State.NONE);
    }

    @Test
    void 時刻のない印は期限を過ぎたものとして扱う() {
        MockHttpSession session = marked(actor);
        session.removeAttribute(SessionKeys.RECOVERY_LOGIN_AT);

        assertThat(RecoveryLogin.stateOf(session, actor, LOGGED_IN)).isEqualTo(RecoveryLogin.State.EXPIRED);
    }

    @Test
    void 消すと印も時刻もなくなる() {
        MockHttpSession session = marked(actor);

        RecoveryLogin.clear(session);

        assertThat(session.getAttribute(SessionKeys.RECOVERY_LOGIN_USER)).isNull();
        assertThat(session.getAttribute(SessionKeys.RECOVERY_LOGIN_AT)).isNull();
    }
}
