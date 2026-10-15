package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
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

    private final Random random = new Random();

    private String hash() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private PasswordResetToken issue(UserId user, UtcInstant at) {
        PasswordResetToken token = PasswordResetToken.issue(UUID.randomUUID(), user, hash(), at);
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
        assertThat(repository.findLastIssuedAt(user)).isEmpty();
        PasswordResetToken first = issue(user, ISSUED);
        issue(user, LATER);

        assertThat(repository.existsByRequestId(first.requestId())).isTrue();
        assertThat(repository.existsByRequestId(UUID.randomUUID())).isFalse();
        assertThat(repository.findLastIssuedAt(user)).contains(LATER);
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
    void 同じ依頼のIDのトークンは2つ記録できない() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);
        PasswordResetToken first = issue(user, ISSUED);

        assertThatThrownBy(() -> repository.add(PasswordResetToken.issue(first.requestId(), user, hash(), LATER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 発行の占有は利用者がいなければfalseを返す() {
        UserId user = AuthenticationFactorFixtures.newUser(companies, users);

        assertThat(repository.lockForIssue(user)).isTrue();
        assertThat(repository.lockForIssue(new UserId(UUID.randomUUID()))).isFalse();
    }
}
