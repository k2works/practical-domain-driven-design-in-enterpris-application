package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotpCredentialTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final UtcInstant ENROLLED_AT = new UtcInstant(Instant.parse("2026-10-11T00:00:00Z"));

    private static TotpCredential lastUsed(long step) {
        return TotpCredential.reconstitute(USER, new EncryptedSecret("ciphertext"), step, ENROLLED_AT);
    }

    @Test
    void 登録したばかりの資格はまだどの刻みも使っていない() {
        TotpCredential credential = TotpCredential.enroll(USER, new EncryptedSecret("ciphertext"), ENROLLED_AT);

        assertThat(credential.userId()).isEqualTo(USER);
        assertThat(credential.lastUsedStep()).isZero();
        assertThat(credential.enrolledAt()).isEqualTo(ENROLLED_AT);
        assertThat(credential.accepts(1)).isTrue();
    }

    @Test
    void 最後に使った刻みより後の刻みだけを受け付ける() {
        TotpCredential credential = lastUsed(100);

        assertThat(credential.accepts(101)).isTrue();
        assertThat(credential.accepts(100)).as("同じ刻みの再利用").isFalse();
        assertThat(credential.accepts(99)).as("前の刻み").isFalse();
    }

    @Test
    void 刻みを使うと最後に使った刻みが進みそれ以下を受け付けなくなる() {
        TotpCredential used = lastUsed(100).use(105);

        assertThat(used.lastUsedStep()).isEqualTo(105);
        assertThat(used.accepts(105)).isFalse();
        assertThat(used.accepts(104)).isFalse();
        assertThat(used.accepts(106)).isTrue();
    }

    @Test
    void 受け付けない刻みは使えない() {
        TotpCredential credential = lastUsed(100);

        assertThatThrownBy(() -> credential.use(100)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 負の刻みは失敗へ倒す() {
        TotpCredential credential = lastUsed(0);

        assertThatThrownBy(() -> credential.accepts(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lastUsed(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
