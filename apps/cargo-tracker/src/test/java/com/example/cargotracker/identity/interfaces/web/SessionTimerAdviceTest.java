package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 入力の控え（Bolt 32f）の利用者の鍵。画面に UUID を出さず、同じ利用者かどうかだけが分かる値であること。 */
class SessionTimerAdviceTest {

    private static final UserId USER = new UserId(UUID.fromString("0b8f5c2e-6f0a-4d7e-9d0c-3a1f2b4c5d6e"));

    @Test
    void 利用者の鍵は同じ利用者で同じ値になる() {
        assertThat(SessionTimerAdvice.ownerKey(USER)).isEqualTo(SessionTimerAdvice.ownerKey(new UserId(USER.value())));
    }

    @Test
    void 利用者の鍵は別の利用者で違う値になる() {
        assertThat(SessionTimerAdvice.ownerKey(USER))
                .isNotEqualTo(SessionTimerAdvice.ownerKey(new UserId(UUID.randomUUID())));
    }

    @Test
    void 利用者の鍵は16文字のURLで使える文字だけでUUIDを含まない() {
        String key = SessionTimerAdvice.ownerKey(USER);

        assertThat(key).hasSize(16).matches("[A-Za-z0-9_-]{16}");
        assertThat(USER.value().toString()).doesNotContain(key);
        assertThat(key).doesNotContain(USER.value().toString().substring(0, 8));
    }
}
