package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 再設定のトークン（IA-INV-12、SEC-08。Bolt 33）。 */
class PasswordResetTokenTest {

    private static final Instant ISSUED = Instant.parse("2026-10-15T01:00:00Z");
    private static final String HASH = "a".repeat(64);

    private static UtcInstant at(Instant instant) {
        return new UtcInstant(instant);
    }

    private static PasswordResetToken issued() {
        return PasswordResetToken.issue(UUID.randomUUID(), new UserId(UUID.randomUUID()), HASH, at(ISSUED));
    }

    @Test
    void 発行したトークンは30分の期限を持ちハッシュだけを持つ() {
        PasswordResetToken token = issued();

        assertThat(token.expiresAt()).isEqualTo(at(ISSUED.plus(Duration.ofMinutes(30))));
        assertThat(token.tokenHash()).isEqualTo(HASH);
        assertThat(token.usedAt()).isEmpty();
        assertThat(token.invalidatedAt()).isEmpty();
    }

    @Test
    void 期限の前は使え期限からは使えない() {
        PasswordResetToken token = issued();

        assertThat(token.isUsable(at(ISSUED.plus(Duration.ofMinutes(30)).minusSeconds(1))))
                .isTrue();
        assertThat(token.isUsable(at(ISSUED.plus(Duration.ofMinutes(30))))).isFalse();
    }

    @Test
    void 無効にしたトークンは期限の前でも使えない() {
        PasswordResetToken token = issued();
        assertThat(token.isUsable(at(ISSUED.plusSeconds(10)))).isTrue();

        PasswordResetToken invalidated = token.invalidate(at(ISSUED.plusSeconds(10)));

        assertThat(invalidated.isUsable(at(ISSUED.plusSeconds(11)))).isFalse();
        assertThat(invalidated.invalidatedAt()).contains(at(ISSUED.plusSeconds(10)));
    }

    @Test
    void 無効にしたトークンはもう一度無効にできない() {
        PasswordResetToken invalidated = issued().invalidate(at(ISSUED.plusSeconds(10)));
        UtcInstant later = at(ISSUED.plusSeconds(20));

        assertThatThrownBy(() -> invalidated.invalidate(later)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 前の発行から1分たつまでは発行し直さない() {
        UtcInstant previous = at(ISSUED);

        assertThat(PasswordResetToken.allowsIssue(Optional.of(previous), at(ISSUED.plusSeconds(59))))
                .isFalse();
        assertThat(PasswordResetToken.allowsIssue(Optional.of(previous), at(ISSUED.plusSeconds(60))))
                .isTrue();
        assertThat(PasswordResetToken.allowsIssue(Optional.empty(), at(ISSUED))).isTrue();
    }

    @Test
    void ハッシュはSHA256の16進64文字に限る() {
        UserId user = new UserId(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        UtcInstant issuedAt = at(ISSUED);
        String tooShort = "a".repeat(63);
        String upperCase = "A".repeat(64);

        assertThatThrownBy(() -> PasswordResetToken.issue(requestId, user, tooShort, issuedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordResetToken.issue(requestId, user, upperCase, issuedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 文字列にハッシュを出さない() {
        assertThat(issued().toString()).doesNotContain(HASH);
    }
}
