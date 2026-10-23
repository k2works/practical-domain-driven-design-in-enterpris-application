package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.application.internal.eventhandlers.PasswordResetMailEventHandler;
import com.example.cargotracker.identity.domain.events.PasswordResetRequested;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * password の再設定の依頼から、コミットの後のトークンの発行とメールの送信まで（US-18 AC8 の前半、SEC-08、IA-INV-12、ADR-017。Bolt 33）。
 * 本物の PostgreSQL と Mailpit で確かめる。同じコンテキストを共有するので、記録は利用者と時刻で絞る（T-113）。
 */
@IdentityIntegrationTest
class PasswordResetRequestIntegrationTest {

    private static final Pattern RESET_LINK =
            Pattern.compile("http://localhost:8080/password-reset/([A-Za-z0-9_-]{43})");
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    PasswordResetRequestService service;

    @Autowired
    PasswordResetMailEventHandler handler;

    @Autowired
    PasswordResetTokenRepository tokens;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    MutableClock clock;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private UserId newUser() {
        return transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
    }

    private EmailAddress emailOf(UserId user) {
        return users.findById(user).orElseThrow().email();
    }

    private MailpitClient mailpit() {
        return new MailpitClient(mailpitContainer);
    }

    private int tokenCount(UserId user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?", Integer.class, user.value());
    }

    /** 利用者の再設定の依頼の監査（共有の表なので利用者で絞る。T-113）。 */
    private List<Map<String, Object>> auditsOf(UserId user) {
        return jdbc.queryForList(
                "SELECT result, reason FROM identity.audit_record"
                        + " WHERE action = 'PASSWORD_RESET_REQUESTED' AND actor_user_id = ?",
                user.value());
    }

    /** 時刻で絞った、操作者のない（登録のないアドレスの）再設定の依頼の監査の件数。 */
    private int unknownAuditsAt(Instant at) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'PASSWORD_RESET_REQUESTED'"
                        + " AND actor_user_id IS NULL AND actor_company_id IS NULL AND result = 'FAILURE'"
                        + " AND reason = 'UNKNOWN_USER' AND occurred_at = ?",
                Integer.class,
                java.sql.Timestamp.from(at));
    }

    /** その利用者の DE-23 の発行の記録の件数（完了したかを問わない）。 */
    private int publications(UserId user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?",
                Integer.class,
                PasswordResetRequested.class.getName(),
                "%" + user.value() + "%");
    }

    /** その利用者の DE-23 の発行の記録が、指定の件数だけ完了するまで待つ（ハンドラーが動き終えた印）。 */
    private void awaitCompletedPublications(UserId user, int count) {
        await().atMost(WAIT)
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                                Integer.class,
                                PasswordResetRequested.class.getName(),
                                "%" + user.value() + "%")
                        == count);
    }

    private static String tokenIn(ReceivedMail mail) {
        Matcher matcher = RESET_LINK.matcher(mail.text());
        assertThat(matcher.find()).as("本文に再設定のリンクがある").isTrue();
        return matcher.group(1);
    }

    @Test
    void 有効な利用者の依頼は監査に成功を書きコミットの後にトークンを発行してリンクを送る() {
        UserId user = newUser();
        EmailAddress email = emailOf(user);
        Instant now = Instant.parse("2026-10-15T02:00:00Z");
        clock.setInstant(now);

        service.request(email);

        List<ReceivedMail> mails = mailpit().awaitMessagesTo(email.value(), 1, WAIT);
        // メールはハンドラーのコミットの前に届くので、表を読む前にハンドラーの完了を待つ
        awaitCompletedPublications(user, 1);
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(tokenIn(mail)).hasSize(43);
            assertThat(mail.text()).contains("このリンクは 2026-10-15 11:30 Asia/Tokyo（UTC+09:00） まで使えます");
        });
        assertThat(auditsOf(user))
                .singleElement()
                .satisfies(row ->
                        assertThat(row).containsEntry("result", "SUCCESS").containsEntry("reason", null));
        assertThat(tokenCount(user)).isEqualTo(1);
    }

    @Test
    void 平文のトークンとリンクは表と発行記録に残らない() {
        UserId user = newUser();
        EmailAddress email = emailOf(user);
        clock.setInstant(Instant.parse("2026-10-15T02:10:00Z"));

        service.request(email);

        String token = tokenIn(mailpit().awaitMessagesTo(email.value(), 1, WAIT).getFirst());
        awaitCompletedPublications(user, 1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM platform.event_publication WHERE serialized_event LIKE ?",
                        Integer.class,
                        "%" + token + "%"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification.notification WHERE recipient_user_id = ?"
                                + " AND (target_ref LIKE ? OR locale LIKE ? OR COALESCE(failure_reason, '') LIKE ?)",
                        Integer.class,
                        user.value(),
                        "%" + token + "%",
                        "%" + token + "%",
                        "%" + token + "%"))
                .as("通知の記録に平文がない")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification.notification WHERE recipient_user_id = ?",
                        Integer.class,
                        user.value()))
                .as("同じ利用者の通知はある（空振りしない）")
                .isEqualTo(1);
        assertThat(tokens.findByTokenHash(sha256(token))).as("表にはハッシュがある").isPresent();
    }

    @Test
    void 登録のないメールアドレスと止められた利用者には送らず監査に理由を書く() {
        UserId suspended = newUser();
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE id = ?", suspended.value());
        EmailAddress unknown = EmailAddress.of("unknown-" + UUID.randomUUID() + "@example.com");
        UserId control = newUser();
        Instant now = Instant.parse("2026-10-15T02:21:00Z");
        clock.setInstant(now);

        // 否定の依頼を先に出し、最後に出した有効な利用者の依頼が届くまで待ってから確かめる（T-119。空振りしない）
        service.request(emailOf(suspended));
        service.request(unknown);
        service.request(emailOf(control));
        assertThat(mailpit().awaitMessagesTo(emailOf(control).value(), 1, WAIT)).hasSize(1);
        awaitCompletedPublications(control, 1);

        assertThat(auditsOf(suspended))
                .singleElement()
                .satisfies(row ->
                        assertThat(row).containsEntry("result", "FAILURE").containsEntry("reason", "SUSPENDED"));
        assertThat(unknownAuditsAt(now)).as("登録のないアドレスは操作者を残さない").isEqualTo(1);
        assertThat(publications(suspended))
                .as("止められた利用者には DE-23 を出さない（サービスの判定）")
                .isZero();
        assertThat(tokenCount(suspended)).isZero();
        assertThat(mailpit().messagesTo(emailOf(suspended).value())).isEmpty();
        assertThat(mailpit().messagesTo(unknown.value())).isEmpty();
    }

    @Test
    void 依頼の後に止められた利用者にはハンドラーが発行しない() {
        UserId user = newUser();
        UserId control = newUser();
        clock.setInstant(Instant.parse("2026-10-15T02:25:00Z"));
        UtcInstant now = new UtcInstant(clock.instant());
        PasswordResetMailEventHandler target = AopTestUtils.getTargetObject(handler);
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE id = ?", user.value());

        transactionTemplate.executeWithoutResult(
                status -> target.on(new PasswordResetRequested(UUID.randomUUID(), control, now)));
        transactionTemplate.executeWithoutResult(
                status -> target.on(new PasswordResetRequested(UUID.randomUUID(), user, now)));

        assertThat(tokenCount(control)).as("有効な利用者には発行する（対照）").isEqualTo(1);
        assertThat(tokenCount(user)).isZero();
        assertThat(mailpit().messagesTo(emailOf(user).value())).isEmpty();
    }

    @Test
    void 前の発行から1分たつまでは送らず1分たてば送り前のリンクを無効にする() {
        UserId user = newUser();
        EmailAddress email = emailOf(user);
        Instant first = Instant.parse("2026-10-15T02:30:00Z");
        clock.setInstant(first);
        service.request(email);
        String firstToken =
                tokenIn(mailpit().awaitMessagesTo(email.value(), 1, WAIT).getFirst());
        awaitCompletedPublications(user, 1);

        clock.setInstant(first.plusSeconds(59));
        service.request(email);
        awaitCompletedPublications(user, 2);
        assertThat(tokenCount(user)).isEqualTo(1);
        assertThat(mailpit().messagesTo(email.value())).hasSize(1);

        clock.setInstant(first.plusSeconds(60));
        service.request(email);
        assertThat(mailpit().awaitMessagesTo(email.value(), 2, WAIT)).hasSize(2);
        awaitCompletedPublications(user, 3);
        assertThat(tokenCount(user)).isEqualTo(2);
        assertThat(tokens.findByTokenHash(sha256(firstToken)).orElseThrow().invalidatedAt())
                .contains(new UtcInstant(first.plusSeconds(60)));
    }

    @Test
    void 依頼をロールバックしたらトークンを発行しない() {
        UserId user = newUser();
        UserId control = newUser();
        clock.setInstant(Instant.parse("2026-10-15T02:40:00Z"));

        transactionTemplate.executeWithoutResult(status -> {
            service.request(emailOf(user));
            status.setRollbackOnly();
        });
        service.request(emailOf(control));

        // ロールバックしなかった依頼が届くまで待ってから、ロールバックした依頼には何もないことを確かめる
        assertThat(mailpit().awaitMessagesTo(emailOf(control).value(), 1, WAIT)).hasSize(1);
        awaitCompletedPublications(control, 1);
        assertThat(publications(user)).as("ロールバックした依頼の DE-23 は記録されない").isZero();
        assertThat(tokenCount(user)).isZero();
        assertThat(mailpit().messagesTo(emailOf(user).value())).isEmpty();
    }

    @Test
    void 同じ依頼の再配信ではトークンを2つ作らずメールも2通送らない() {
        UserId user = newUser();
        EmailAddress email = emailOf(user);
        clock.setInstant(Instant.parse("2026-10-15T02:50:00Z"));
        PasswordResetRequested event =
                new PasswordResetRequested(UUID.randomUUID(), user, new UtcInstant(clock.instant()));
        PasswordResetMailEventHandler target = AopTestUtils.getTargetObject(handler);

        transactionTemplate.executeWithoutResult(status -> target.on(event));
        clock.setInstant(Instant.parse("2026-10-15T02:52:00Z"));
        transactionTemplate.executeWithoutResult(status -> target.on(event));

        assertThat(tokenCount(user)).isEqualTo(1);
        assertThat(mailpit().awaitMessagesTo(email.value(), 1, WAIT)).hasSize(1);
    }

    @Test
    void 同じ利用者への同時の2つの発行は後が先を待ち1つだけ発行する() {
        UserId user = newUser();
        EmailAddress email = emailOf(user);
        clock.setInstant(Instant.parse("2026-10-15T03:00:00Z"));
        UtcInstant now = new UtcInstant(clock.instant());
        PasswordResetMailEventHandler target = AopTestUtils.getTargetObject(handler);

        // 先の発行のコミットを止め、後の発行が利用者の行の占有で待つことを確かめてから先をコミットする（T-100）
        OverlappingTransactions.run(
                transactionTemplate,
                () -> {
                    target.on(new PasswordResetRequested(UUID.randomUUID(), user, now));
                    return Boolean.TRUE;
                },
                () -> {
                    target.on(new PasswordResetRequested(UUID.randomUUID(), user, now));
                    return Boolean.TRUE;
                });

        assertThat(tokenCount(user)).isEqualTo(1);
        assertThat(mailpit().awaitMessagesTo(email.value(), 1, WAIT)).hasSize(1);
    }

    private static String sha256(String token) {
        return new com.example.cargotracker.identity.infrastructure.crypto.Sha256PasswordResetTokenHasher().hash(token);
    }
}
