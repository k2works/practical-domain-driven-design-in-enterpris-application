package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 再設定のトークンのリポジトリと表（PostgreSQL。IA-INV-12。Bolt 33）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisPasswordResetTokenRepositoryIntegrationTest {

    private static final UtcInstant ISSUED = new UtcInstant(Instant.parse("2026-10-15T01:00:00.123456Z"));
    private static final UtcInstant LATER = new UtcInstant(Instant.parse("2026-10-15T01:05:00.654321Z"));

    @Autowired
    MyBatisPasswordResetTokenRepository repository;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private final Random random = new Random();

    private String hash() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private PasswordResetToken issue(UserId user, UtcInstant at) {
        return issue(PasswordResetTokenPurpose.RESET, user, at);
    }

    private PasswordResetToken issue(PasswordResetTokenPurpose purpose, UserId user, UtcInstant at) {
        PasswordResetToken token = PasswordResetToken.issue(purpose, UUID.randomUUID(), user, hash(), at);
        repository.add(token);
        return token;
    }

    @Test
    void 発行したトークンをハッシュで読み出す() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken token = issue(user, ISSUED);

        PasswordResetToken found = repository.findByTokenHash(token.tokenHash()).orElseThrow();

        assertThat(found.id()).isEqualTo(token.id());
        assertThat(found.requestId()).isEqualTo(token.requestId());
        assertThat(found.userId()).isEqualTo(user);
        assertThat(found.issuedAt()).isEqualTo(ISSUED);
        assertThat(found.expiresAt()).isEqualTo(token.expiresAt());
        assertThat(found.usedAt()).isEmpty();
        assertThat(found.invalidatedAt()).isEmpty();
    }

    @Test
    void 依頼のIDのトークンがあるかと最後の発行時刻を返す() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        assertThat(repository.findLastIssuedAt(user, PasswordResetTokenPurpose.RESET))
                .isEmpty();
        PasswordResetToken first = issue(user, ISSUED);
        issue(user, LATER);

        assertThat(repository.existsByRequestId(first.requestId())).isTrue();
        assertThat(repository.existsByRequestId(UUID.randomUUID())).isFalse();
        assertThat(repository.findLastIssuedAt(user, PasswordResetTokenPurpose.RESET))
                .contains(LATER);
    }

    @Test
    void 再設定の最後の発行は利用者の招待のトークンを数えない() {
        // 行 7（Bolt 38b。招待の直後の本人の再設定の依頼を止めない）。招待のトークンがあることを先に確かめる
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken invitation = issue(PasswordResetTokenPurpose.USER_INVITATION, user, ISSUED);
        assertThat(repository.findByTokenHash(invitation.tokenHash())).isPresent();
        assertThat(repository.findLastIssuedAt(user, PasswordResetTokenPurpose.USER_INVITATION))
                .contains(ISSUED);

        assertThat(repository.findLastIssuedAt(user, PasswordResetTokenPurpose.RESET))
                .isEmpty();
    }

    @Test
    void 用途と期限を保存して読み戻し用途を省いた行は再設定になる() {
        // 行 9（Bolt 38b）
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken invitation = issue(PasswordResetTokenPurpose.USER_INVITATION, user, ISSUED);

        PasswordResetToken read =
                repository.findByTokenHash(invitation.tokenHash()).orElseThrow();
        assertThat(read.purpose()).isEqualTo(PasswordResetTokenPurpose.USER_INVITATION);
        assertThat(read.expiresAt()).isEqualTo(invitation.expiresAt());

        String legacyHash = hash();
        jdbc.update(
                "INSERT INTO identity.password_reset_token (id, request_id, user_id, token_hash, issued_at, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                UUID.randomUUID(),
                user.value(),
                legacyHash,
                java.sql.Timestamp.from(ISSUED.instant()),
                java.sql.Timestamp.from(ISSUED.instant().plusSeconds(1800)));
        assertThat(repository.findByTokenHash(legacyHash).orElseThrow().purpose())
                .isEqualTo(PasswordResetTokenPurpose.RESET);
    }

    @Test
    void 再設定の発行の前の無効化は未使用の利用者の招待のトークンも無効にする() {
        // 行 10（Bolt 38b。新しいリンクが前のリンクに代わる IA-INV-12 を用途をまたいで保つ）
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken invitation = issue(PasswordResetTokenPurpose.USER_INVITATION, user, ISSUED);

        assertThat(repository.invalidateUnused(user, LATER)).isEqualTo(1);

        assertThat(repository
                        .findByTokenHash(invitation.tokenHash())
                        .orElseThrow()
                        .invalidatedAt())
                .contains(LATER);
    }

    @Test
    void 利用者の未使用のトークンだけを無効にする() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        UserId other = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken first = issue(user, ISSUED);
        PasswordResetToken othersToken = issue(other, ISSUED);

        assertThat(repository.invalidateUnused(user, LATER)).isEqualTo(1);
        assertThat(repository.invalidateUnused(user, LATER))
                .as("無効にしたトークンはもう数えない")
                .isZero();

        assertThat(repository.findByTokenHash(first.tokenHash()).orElseThrow().invalidatedAt())
                .contains(LATER);
        assertThat(repository
                        .findByTokenHash(othersToken.tokenHash())
                        .orElseThrow()
                        .invalidatedAt())
                .as("ほかの利用者のトークンは変えない")
                .isEmpty();
    }

    @Test
    void IDでトークンを読み出す() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken token = issue(user, ISSUED);

        assertThat(repository.findById(token.id()))
                .hasValueSatisfying(found -> assertThat(found.tokenHash()).isEqualTo(token.tokenHash()));
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 使えるトークンは1回だけ使えドメインの判定と同じ条件で使えないトークンは使えない() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken usable = issue(user, ISSUED);
        PasswordResetToken expired = issue(user, ISSUED);
        PasswordResetToken invalidated = issue(AuthenticationFactorFixtures.newUser(companies, users), ISSUED);
        repository.invalidateUnused(invalidated.userId(), LATER);
        UtcInstant beforeExpiry = new UtcInstant(usable.expiresAt().instant().minusNanos(1_000));
        UtcInstant atExpiry = expired.expiresAt();

        // 契約: SQL の条件（markUsed）とドメインの isUsable が同じ答えを返す（IA-INV-12。LockPolicy の前例）
        assertThat(usable.isUsable(beforeExpiry)).isTrue();
        assertThat(repository.markUsed(usable.id(), beforeExpiry)).isTrue();
        assertThat(repository.markUsed(usable.id(), beforeExpiry))
                .as("使ったトークンはもう使えない")
                .isFalse();
        assertThat(repository.findById(usable.id()).orElseThrow().isUsable(beforeExpiry))
                .isFalse();

        assertThat(expired.isUsable(atExpiry)).isFalse();
        assertThat(repository.markUsed(expired.id(), atExpiry))
                .as("期限ちょうどは使えない")
                .isFalse();

        PasswordResetToken readInvalidated =
                repository.findById(invalidated.id()).orElseThrow();
        assertThat(readInvalidated.isUsable(LATER)).isFalse();
        assertThat(repository.markUsed(invalidated.id(), LATER)).isFalse();
        assertThat(repository.markUsed(UUID.randomUUID(), LATER))
                .as("存在しないトークン")
                .isFalse();
    }

    @Test
    void 使ったトークンは無効にしない() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken unused = issue(user, ISSUED);
        PasswordResetToken used = PasswordResetToken.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                user,
                PasswordResetTokenPurpose.RESET,
                hash(),
                ISSUED,
                new UtcInstant(ISSUED.instant().plus(PasswordResetTokenPurpose.RESET.validity())),
                LATER,
                null);
        repository.add(used);

        assertThat(repository.invalidateUnused(user, LATER)).isEqualTo(1);

        assertThat(repository.findByTokenHash(unused.tokenHash()).orElseThrow().invalidatedAt())
                .as("未使用のトークンは無効にする（対照）")
                .contains(LATER);
        assertThat(repository.findByTokenHash(used.tokenHash()).orElseThrow().invalidatedAt())
                .as("使ったトークンは変えない")
                .isEmpty();
    }

    @Test
    void 同じ依頼のIDのトークンは2つ記録できない() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken first = issue(user, ISSUED);
        PasswordResetToken sameRequest =
                PasswordResetToken.issue(PasswordResetTokenPurpose.RESET, first.requestId(), user, hash(), LATER);

        assertThatThrownBy(() -> repository.add(sameRequest)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 発行の占有は利用者がいなければfalseを返す() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);

        assertThat(repository.lockForIssue(user)).isTrue();
        assertThat(repository.lockForIssue(new UserId(UUID.randomUUID()))).isFalse();
    }
}
