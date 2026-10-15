package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.OverlappingTransactions;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordPolicyViolation;
import com.example.cargotracker.identity.infrastructure.crypto.Sha256PasswordResetTokenHasher;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * password の再設定のサービス（US-18 AC8 の後半、SEC-04・SEC-05・SEC-08、IA-INV-12。Bolt 33a）。本物の PostgreSQL で確かめる。共有の表は
 * 利用者で絞る（T-113）。
 */
@IdentityIntegrationTest
class PasswordResetCompletionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");
    private static final String NEW_PASSWORD = "a brand new long passphrase";

    @Autowired
    PasswordResetCompletionService service;

    @Autowired
    PasswordResetTokenRepository tokens;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    LoginFailureRepository failures;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    MutableClock clock;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private final Sha256PasswordResetTokenHasher hasher = new Sha256PasswordResetTokenHasher();

    @BeforeEach
    void 時計を合わせる() {
        clock.setInstant(NOW);
    }

    private UserId newUser() {
        return transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
    }

    /** 平文のトークンを発行して保存し、平文を返す。 */
    private String issue(UserId user, Instant at) {
        String token = "token-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status ->
                tokens.add(PasswordResetToken.issue(UUID.randomUUID(), user, hasher.hash(token), new UtcInstant(at))));
        return token;
    }

    private UUID opened(String token) {
        return service.open(token).orElseThrow();
    }

    private String passwordHashOf(UserId user) {
        return users.findById(user).orElseThrow().passwordHash();
    }

    private int completedAudits(UserId user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'PASSWORD_RESET_COMPLETED'"
                        + " AND actor_user_id = ? AND result = 'SUCCESS'",
                Integer.class,
                user.value());
    }

    private String sessionOf(String principal) {
        return save(sessions, principal);
    }

    private static <S extends Session> String save(FindByIndexNameSessionRepository<S> repository, String principal) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);
        repository.save(session);
        return session.getId();
    }

    @Test
    void リンクの値が使えるトークンならIDを返し使えなければ空() {
        UserId user = newUser();
        String token = issue(user, NOW.minusSeconds(60));

        assertThat(service.open(token)).isPresent();
        assertThat(service.open("unknown-token")).isEmpty();
        assertThat(service.open(" ")).isEmpty();
        clock.setInstant(NOW.plusSeconds(30 * 60));
        assertThat(service.open(token)).as("発行から 30 分を過ぎた").isEmpty();
    }

    @Test
    void 設定するとpasswordを変えトークンを使い前のトークンを無効にし監査を書きsessionを失効させロックは変えない() {
        UserId user = newUser();
        String email = users.findById(user).orElseThrow().email().value();
        String older = issue(user, NOW.minusSeconds(120));
        String token = issue(user, NOW.minusSeconds(60));
        UUID olderId = opened(older);
        UUID tokenId = opened(token);
        failures.prepare(user, new UtcInstant(NOW));
        String session = sessionOf(email);
        jdbc.update(
                "UPDATE identity.login_failure SET consecutive_failures = 5, locked_until = ? WHERE user_id = ?",
                java.sql.Timestamp.from(NOW.plusSeconds(600)),
                user.value());

        PasswordResetCompletion result = service.complete(tokenId, NEW_PASSWORD);

        assertThat(result).isEqualTo(new PasswordResetCompletion.Completed());
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHashOf(user))).isTrue();
        assertThat(service.isUsable(tokenId)).as("使ったトークン").isFalse();
        assertThat(service.isUsable(olderId)).as("前の未使用のトークン").isFalse();
        assertThat(completedAudits(user)).isEqualTo(1);
        assertThat(sessions.findById(session)).as("その利用者の session を失効させた").isNull();
        assertThat(failures.findByUserId(user).consecutiveFailures())
                .as("ロックは変えない（SEC-02）")
                .isEqualTo(5);
    }

    @Test
    void 方針に合わなければトークンを使わずに理由を返す() {
        UserId user = newUser();
        UUID tokenId = opened(issue(user, NOW.minusSeconds(60)));
        String before = passwordHashOf(user);

        assertThat(service.complete(tokenId, "short"))
                .isEqualTo(new PasswordResetCompletion.PolicyViolated(PasswordPolicyViolation.TOO_SHORT));
        assertThat(service.complete(tokenId, "password1234"))
                .isEqualTo(new PasswordResetCompletion.PolicyViolated(PasswordPolicyViolation.COMMON));
        assertThat(service.complete(tokenId, "あ".repeat(25)))
                .isEqualTo(new PasswordResetCompletion.PolicyViolated(PasswordPolicyViolation.TOO_MANY_BYTES));

        assertThat(service.isUsable(tokenId)).as("直して送り直せる").isTrue();
        assertThat(passwordHashOf(user)).isEqualTo(before);
        assertThat(service.complete(tokenId, NEW_PASSWORD)).isEqualTo(new PasswordResetCompletion.Completed());
    }

    @Test
    void 使えないトークンと止められた利用者は無効としpasswordも監査も変えない() {
        UserId user = newUser();
        UUID used = opened(issue(user, NOW.minusSeconds(60)));
        assertThat(service.complete(used, NEW_PASSWORD)).isEqualTo(new PasswordResetCompletion.Completed());
        String afterFirst = passwordHashOf(user);

        assertThat(service.complete(used, "another long passphrase")).isEqualTo(new PasswordResetCompletion.Invalid());
        assertThat(service.complete(UUID.randomUUID(), NEW_PASSWORD)).isEqualTo(new PasswordResetCompletion.Invalid());
        UUID expiring = opened(issue(user, NOW.minusSeconds(29 * 60)));
        clock.setInstant(NOW.plusSeconds(60));
        assertThat(service.complete(expiring, "another long passphrase"))
                .as("期限を過ぎた")
                .isEqualTo(new PasswordResetCompletion.Invalid());
        assertThat(passwordHashOf(user)).isEqualTo(afterFirst);
        assertThat(completedAudits(user)).isEqualTo(1);

        UserId suspended = newUser();
        clock.setInstant(NOW);
        UUID suspendedToken = opened(issue(suspended, NOW.minusSeconds(60)));
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE id = ?", suspended.value());
        assertThat(service.complete(suspendedToken, NEW_PASSWORD)).isEqualTo(new PasswordResetCompletion.Invalid());
        assertThat(service.isUsable(suspendedToken)).as("止められた利用者のトークンは使わない").isTrue();
        assertThat(completedAudits(suspended)).isZero();
    }

    @Test
    void 同じトークンの同時の2つの設定は後が先を待ち1回だけ変える() {
        UserId user = newUser();
        UUID tokenId = opened(issue(user, NOW.minusSeconds(60)));

        // 先がトークンを使ってコミットを止め、後の設定がトークンの行で待つことを確かめてから先をコミットする（T-100）
        List<Object> results = OverlappingTransactions.<Object>runSecondOutsideTransaction(
                transactionTemplate,
                () -> tokens.markUsed(tokenId, new UtcInstant(NOW)),
                () -> service.complete(tokenId, NEW_PASSWORD));

        assertThat(results.get(0)).isEqualTo(Boolean.TRUE);
        assertThat(results.get(1)).isEqualTo(new PasswordResetCompletion.Invalid());
        assertThat(completedAudits(user)).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHashOf(user))).isFalse();
    }
}
