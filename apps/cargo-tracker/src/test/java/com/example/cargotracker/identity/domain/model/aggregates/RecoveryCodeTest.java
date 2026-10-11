package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecoveryCodeTest {

    /** 偽のハッシュ。接頭辞を付けるだけ（本物の bcrypt は identity.infrastructure）。 */
    private static final RecoveryCodeHasher HASHER = new RecoveryCodeHasher() {
        @Override
        public String hash(String code) {
            return "hashed:" + code;
        }

        @Override
        public boolean matches(String code, String hash) {
            return hash.equals("hashed:" + code);
        }
    };

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final UtcInstant ISSUED_AT = new UtcInstant(Instant.parse("2026-10-11T00:00:00Z"));
    private static final UtcInstant USED_AT = new UtcInstant(Instant.parse("2026-10-11T01:00:00Z"));

    @Test
    void 発行するとハッシュだけを持ち平文を持たない() {
        RecoveryCode code = RecoveryCode.issue(USER, "abcd-efgh", HASHER, ISSUED_AT);

        assertThat(code.userId()).isEqualTo(USER);
        assertThat(code.codeHash()).isEqualTo("hashed:abcd-efgh");
        assertThat(code.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(code.isUsed()).isFalse();
        assertThat(code.toString()).doesNotContain("abcd-efgh");
    }

    @Test
    void 未使用のコードは同じ平文に一致し違う平文に一致しない() {
        RecoveryCode code = RecoveryCode.issue(USER, "abcd-efgh", HASHER, ISSUED_AT);

        assertThat(code.matches("abcd-efgh", HASHER)).isTrue();
        assertThat(code.matches("abcd-efgx", HASHER)).isFalse();
    }

    @Test
    void 使ったコードは使った時刻を持ち同じ平文にも一致しない() {
        RecoveryCode used =
                RecoveryCode.issue(USER, "abcd-efgh", HASHER, ISSUED_AT).use(USED_AT);

        assertThat(used.isUsed()).isTrue();
        assertThat(used.usedAt()).contains(USED_AT);
        assertThat(used.matches("abcd-efgh", HASHER)).isFalse();
    }

    @Test
    void 使ったコードはもう一度使えない() {
        RecoveryCode used =
                RecoveryCode.issue(USER, "abcd-efgh", HASHER, ISSUED_AT).use(USED_AT);

        assertThatThrownBy(() -> used.use(USED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 空の平文は発行できない() {
        assertThatThrownBy(() -> RecoveryCode.issue(USER, " ", HASHER, ISSUED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<RecoveryCode> issue(UserId userId, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> RecoveryCode.issue(userId, "code-" + i, HASHER, ISSUED_AT))
                .toList();
    }

    @Test
    void 発行する組はその利用者の未使用の10個() {
        assertThat(RecoveryCode.requireIssuedSet(USER, issue(USER, 10))).hasSize(10);
    }

    @Test
    void 発行する組が10個でなければ失敗へ倒す() {
        List<RecoveryCode> nine = issue(USER, 9);
        List<RecoveryCode> eleven = issue(USER, 11);

        assertThatThrownBy(() -> RecoveryCode.requireIssuedSet(USER, nine))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryCode.requireIssuedSet(USER, eleven))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryCode.requireIssuedSet(USER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ほかの利用者のコードや使ったコードが混ざった組は失敗へ倒す() {
        List<RecoveryCode> withOthers = new ArrayList<>(issue(USER, 9));
        withOthers.add(RecoveryCode.issue(new UserId(UUID.randomUUID()), "other", HASHER, ISSUED_AT));
        List<RecoveryCode> withUsed = new ArrayList<>(issue(USER, 9));
        withUsed.add(RecoveryCode.issue(USER, "used", HASHER, ISSUED_AT).use(USED_AT));

        assertThatThrownBy(() -> RecoveryCode.requireIssuedSet(USER, withOthers))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryCode.requireIssuedSet(USER, withUsed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 利用者ごとに発行する数は10() {
        assertThat(RecoveryCode.CODES_PER_USER).isEqualTo(10);
    }
}
