package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpMatch;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotpCredentialTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final UtcInstant ENROLLED_AT = new UtcInstant(Instant.parse("2026-10-11T00:00:00Z"));

    private static TotpCredential lastUsed(long step) {
        return TotpCredential.reconstitute(USER, new EncryptedSecret("ciphertext"), step, ENROLLED_AT);
    }

    @Test
    void 再登録は秘密と登録の時刻を置き換え最後に使った刻みは大きい方にする() {
        UtcInstant later = new UtcInstant(Instant.parse("2026-10-16T00:00:00Z"));
        EncryptedSecret renewed = new EncryptedSecret("renewed");

        TotpCredential behind = lastUsed(100).reEnroll(renewed, 90, later);
        TotpCredential ahead = lastUsed(100).reEnroll(renewed, 120, later);

        assertThat(behind.secret()).isEqualTo(renewed);
        assertThat(behind.enrolledAt()).isEqualTo(later);
        assertThat(behind.lastUsedStep()).as("新しいコードの刻みが前でも戻さない（IA-INV-10）").isEqualTo(100);
        assertThat(ahead.lastUsedStep()).as("新しいコードの刻みは使ったことにする").isEqualTo(120);
        assertThat(ahead.userId()).isEqualTo(USER);
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

    /** 偽の生成器。秘密を使わず、刻みの番号を 6 桁にしたものをコードにする。 */
    private static final TotpCodeGenerator GENERATOR = (owner, secret, step) -> String.format("%06d", step % 1_000_000);

    @Test
    void 時刻の刻みは30秒ごとの番号() {
        assertThat(TotpCredential.stepAt(new UtcInstant(Instant.ofEpochSecond(59))))
                .isEqualTo(1);
        assertThat(TotpCredential.stepAt(new UtcInstant(Instant.ofEpochSecond(60))))
                .isEqualTo(2);
    }

    @Test
    void 前後1つの刻みのコードを受け付けそれより外は受け付けない() {
        TotpCredential credential = lastUsed(0);

        assertThat(credential.match("000100", 100, GENERATOR)).isEqualTo(new TotpMatch.Matched(100));
        assertThat(credential.match("000099", 100, GENERATOR)).isEqualTo(new TotpMatch.Matched(99));
        assertThat(credential.match("000101", 100, GENERATOR)).isEqualTo(new TotpMatch.Matched(101));
        assertThat(credential.match("000098", 100, GENERATOR)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match("000102", 100, GENERATOR)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match("123456", 100, GENERATOR)).isEqualTo(new TotpMatch.Mismatch());
    }

    @Test
    void 窓の中でも最後に使った刻み以下のコードは受け付けない() {
        TotpCredential credential = lastUsed(100);

        assertThat(credential.match("000099", 100, GENERATOR)).as("使った刻みより前で一致").isEqualTo(new TotpMatch.Reused());
        assertThat(credential.match("000100", 100, GENERATOR)).as("同じコードの再利用").isEqualTo(new TotpMatch.Reused());
        assertThat(credential.match("000101", 100, GENERATOR)).isEqualTo(new TotpMatch.Matched(101));
        assertThat(credential.match("000098", 100, GENERATOR))
                .as("窓の外は使った刻み以下でも再利用でなく不一致")
                .isEqualTo(new TotpMatch.Mismatch());
    }

    @Test
    void 同じコードが使った刻みと受け付ける刻みの両方に一致したら受け付ける刻みを返す() {
        TotpCredential credential = lastUsed(100);
        TotpCodeGenerator sameCode =
                (owner, secret, step) -> step == 100 ? "999999" : (step == 99 || step == 101 ? "123456" : "000000");

        assertThat(credential.match("123456", 100, sameCode)).isEqualTo(new TotpMatch.Matched(101));
    }

    @Test
    void 形の違うコードは生成器を呼ばずに受け付けない() {
        TotpCredential credential = lastUsed(0);
        TotpCodeGenerator mustNotBeCalled = (owner, secret, step) -> {
            throw new AssertionError("形の違うコードで生成器を呼んだ");
        };

        assertThat(credential.match(null, 100, mustNotBeCalled)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match("", 100, mustNotBeCalled)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match(" 000100", 100, mustNotBeCalled)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match("00010a", 100, mustNotBeCalled)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match("０００１００", 100, mustNotBeCalled)).as("全角の数字").isEqualTo(new TotpMatch.Mismatch());
    }

    @Test
    void 桁数の違うコードは受け付けない() {
        TotpCredential credential = lastUsed(0);

        assertThat(credential.match("00100", 100, GENERATOR)).isEqualTo(new TotpMatch.Mismatch());
        assertThat(credential.match("0000100", 100, GENERATOR)).isEqualTo(new TotpMatch.Mismatch());
    }

    @Test
    void 一致した位置にかかわらず窓のすべての刻みのコードを作って比べる() {
        TotpCredential credential = lastUsed(0);
        for (String code : new String[] {"000099", "000100", "000101", "999999"}) {
            List<Long> generated = new ArrayList<>();
            TotpCodeGenerator counting = (owner, secret, step) -> {
                generated.add(step);
                return GENERATOR.code(owner, secret, step);
            };

            credential.match(code, 100, counting);

            assertThat(generated).as(code).containsExactly(99L, 100L, 101L);
        }
    }

    @Test
    void 刻み0の窓は負の刻みを作らない() {
        TotpCredential credential = lastUsed(0);
        List<Long> generated = new ArrayList<>();
        TotpCodeGenerator rejectsNegative = (owner, secret, step) -> {
            if (step < 0) {
                throw new AssertionError("負の刻みのコードを作った");
            }
            generated.add(step);
            return GENERATOR.code(owner, secret, step);
        };

        assertThat(credential.match("000001", 0, rejectsNegative)).isEqualTo(new TotpMatch.Matched(1));
        assertThat(generated).containsExactly(0L, 1L);
    }

    @Test
    void 生成器には資格が持つ暗号化した秘密を渡し平文の秘密を扱わない() {
        EncryptedSecret own = new EncryptedSecret("v1:own-ciphertext");
        TotpCredential credential = TotpCredential.reconstitute(USER, own, 0, ENROLLED_AT);
        List<EncryptedSecret> given = new ArrayList<>();
        List<UserId> owners = new ArrayList<>();
        TotpCodeGenerator recording = (owner, secret, step) -> {
            given.add(secret);
            owners.add(owner);
            return GENERATOR.code(owner, secret, step);
        };

        credential.match("000100", 100, recording);

        assertThat(given).hasSize(3).containsOnly(own);
        assertThat(owners).as("暗号文の持ち主として自分の利用者を渡す（AAD。Bolt 31b）").containsOnly(USER);
    }

    @Test
    void 今の刻みが負なら失敗へ倒す() {
        TotpCredential credential = lastUsed(0);

        assertThatThrownBy(() -> credential.match("000000", -1, GENERATOR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
