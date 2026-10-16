package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** session の ID（Bolt 33e）。空を拒み、ログに値を出さない。 */
class SessionIdTest {

    @Test
    void 空のsessionのIDは作れない() {
        assertThatThrownBy(() -> new SessionId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionId(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 値で等しくtoStringに値を出さない() {
        SessionId id = new SessionId("3f1c-session");

        assertThat(id).isEqualTo(new SessionId("3f1c-session"));
        assertThat(id.value()).isEqualTo("3f1c-session");
        assertThat(id.toString()).doesNotContain("3f1c");
    }
}
