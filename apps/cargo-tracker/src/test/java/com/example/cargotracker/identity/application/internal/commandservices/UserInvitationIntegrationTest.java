package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.application.internal.eventhandlers.UserInvitationMailEventHandler;
import com.example.cargotracker.identity.domain.events.UserRegistered;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.Sha256PasswordResetTokenHasher;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * 利用者の登録から、コミットの後の招待のトークンの発行とメールの送信まで（US-16 AC1、SEC-08、IA-INV-12、ADR-017。Bolt 38b2c）。本物の
 * PostgreSQL と Mailpit で確かめる。同じコンテキストを共有するので、記録は利用者とメールアドレスで絞る（T-113）。
 */
@IdentityIntegrationTest
class UserInvitationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-26T05:00:00Z");
    private static final Pattern INVITATION_LINK =
            Pattern.compile("http://localhost:8080/password-reset/([A-Za-z0-9_-]{43})");
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    UserRegistrationService service;

    @Autowired
    UserInvitationMailEventHandler handler;

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

    private Company operator;
    private AuthenticatedActor admin;

    @BeforeEach
    void 時計を合わせA社と管理者を置く() {
        clock.setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(operator));
        UserId adminId = new UserId(UUID.randomUUID());
        admin = new AuthenticatedActor(adminId, operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
        transactionTemplate.executeWithoutResult(status -> {
            users.add(User.of(
                    adminId,
                    operator.id(),
                    EmailAddress.of("admin-" + adminId.value() + "@example.com"),
                    "管理 次郎",
                    "{noop}unused",
                    UserStatus.ACTIVE,
                    Set.of(Role.SYSTEM_ADMIN)));
            AdministrationFixtures.assignCompany(jdbc, adminId, operator.id());
        });
    }

    private static EmailAddress newEmail() {
        return EmailAddress.of("invited-" + UUID.randomUUID() + "@example.com");
    }

    private MailpitClient mailpit() {
        return new MailpitClient(mailpitContainer);
    }

    private UserId register(EmailAddress email) {
        UserRegistrationOutcome outcome = service.register(admin, operator.id(), email, "新人 花子", Set.of(Role.SALES));
        assertThat(outcome).isInstanceOf(UserRegistrationOutcome.Registered.class);
        return ((UserRegistrationOutcome.Registered) outcome).userId();
    }

    private int tokenCount(UserId user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?", Integer.class, user.value());
    }

    /** DE-25 の発行の記録の件数（完了したかを問わない。拒否では利用者がいないので、件数の前後の差で見る）。 */
    private int allPublications() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM platform.event_publication WHERE event_type = ?",
                Integer.class,
                UserRegistered.class.getName());
    }

    /** その利用者の DE-25 の発行の記録。 */
    private List<String> publicationsOf(UserId user) {
        return jdbc.queryForList(
                "SELECT serialized_event FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?",
                String.class,
                UserRegistered.class.getName(),
                "%" + user.value() + "%");
    }

    /** その利用者の DE-25 の発行の記録が完了するまで待つ（ハンドラーが動き終えた印）。 */
    private void awaitCompletedPublication(UserId user) {
        await().atMost(WAIT)
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                                Integer.class,
                                UserRegistered.class.getName(),
                                "%" + user.value() + "%")
                        == 1);
    }

    private static String tokenIn(ReceivedMail mail) {
        Matcher matcher = INVITATION_LINK.matcher(mail.text());
        assertThat(matcher.find()).as("本文に初回の設定のリンクがある").isTrue();
        return matcher.group(1);
    }

    private static String sha256(String token) {
        return new Sha256PasswordResetTokenHasher().hash(token);
    }

    @Test
    void 登録するとコミットの後に24時間の招待のトークンを発行して招待のメールを送る() {
        // 行 1
        EmailAddress email = newEmail();

        UserId registered = register(email);

        List<ReceivedMail> mails = mailpit().awaitMessagesTo(email.value(), 1, WAIT);
        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().subject()).isEqualTo("【cargo-tracker】利用者の登録のご案内");
        // メールはハンドラーのコミットの前に届くので、表を読む前にハンドラーの完了を待つ（前例 PasswordResetRequestIntegrationTest）
        awaitCompletedPublication(registered);
        PasswordResetToken issued =
                tokens.findByTokenHash(sha256(tokenIn(mails.getFirst()))).orElseThrow();
        assertThat(issued.purpose()).isEqualTo(PasswordResetTokenPurpose.USER_INVITATION);
        assertThat(issued.userId()).isEqualTo(registered);
        assertThat(issued.expiresAt()).isEqualTo(new UtcInstant(NOW.plus(Duration.ofHours(24))));
        // トークンの request_id は DE-25 の登録の ID で、通知の契機と結ばれる
        assertThat(publicationsOf(registered))
                .singleElement()
                .asString()
                .contains(issued.requestId().toString());
        Map<String, Object> notification = jdbc.queryForMap(
                "SELECT kind, status, target_ref FROM notification.notification WHERE event_id = ?",
                issued.requestId());
        assertThat(notification)
                .containsEntry("kind", "USER_INVITATION")
                .containsEntry("status", "SENT")
                .containsEntry("target_ref", issued.id().toString());
    }

    @Test
    void 平文のトークンとリンクとメールアドレスは表と発行記録とログに残らない() {
        // 行 2（T-206。ログはハンドラーが動き終えるまで取り込む）
        EmailAddress email = newEmail();
        String token;
        UserId registered;
        try (LogCapture logs = LogCapture.start()) {
            registered = register(email);
            token = tokenIn(mailpit().awaitMessagesTo(email.value(), 1, WAIT).getFirst());
            awaitCompletedPublication(registered);

            assertThat(logs.text()).doesNotContain(token).doesNotContain(email.value());
        }
        assertThat(jdbc.queryForList(
                        "SELECT * FROM identity.password_reset_token WHERE user_id = ?", registered.value()))
                .singleElement()
                .satisfies(row -> assertThat(row.values())
                        .noneMatch(value -> String.valueOf(value).contains(token)));
        assertThat(publicationsOf(registered))
                .singleElement()
                .asString()
                .doesNotContain(email.value())
                .doesNotContain(token)
                .doesNotContain("新人 花子");
    }

    @Test
    void 拒否と重複のメールアドレスではDE25を出さずトークンもメールもない() {
        // 行 3（発行記録の件数の前後で見る。拒否では利用者がいない）
        EmailAddress taken = newEmail();
        UserId senior = new UserId(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> users.add(
                User.of(senior, operator.id(), taken, "先輩 一郎", "{noop}unused", UserStatus.ACTIVE, Set.of(Role.SALES))));
        EmailAddress refused = newEmail();
        int before = allPublications();

        assertThat(service.register(admin, operator.id(), taken, "新人 花子", Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Refused.class);
        assertThat(service.register(admin, operator.id(), refused, " ", Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Refused.class);

        assertThat(allPublications()).isEqualTo(before);
        assertThat(tokenCount(senior)).isZero();
        assertThat(mailpit().messagesTo(taken.value())).isEmpty();
        assertThat(mailpit().messagesTo(refused.value())).isEmpty();
    }

    @Test
    void 同じDE25の再配信ではトークンを2つ作らずメールも2通送らない() {
        // 行 4（2 回目の発行を確かめないと、request_id の一意制約で例外になる）
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        EmailAddress email = users.findById(user).orElseThrow().email();
        UserRegistered event = new UserRegistered(UUID.randomUUID(), user, new UtcInstant(NOW));
        UserInvitationMailEventHandler target = AopTestUtils.getTargetObject(handler);

        transactionTemplate.executeWithoutResult(status -> target.on(event));
        clock.setInstant(NOW.plusSeconds(120));
        transactionTemplate.executeWithoutResult(status -> target.on(event));

        assertThat(tokenCount(user)).isEqualTo(1);
        assertThat(mailpit().awaitMessagesTo(email.value(), 1, WAIT)).hasSize(1);
    }

    @Test
    void 登録の後に止められた利用者と無効になった企業の利用者には発行しない() {
        // 行 5
        UserId control = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        UserId suspended =
                transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        UserId inactiveCompany =
                transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE id = ?", suspended.value());
        jdbc.update(
                "UPDATE identity.company SET active = FALSE WHERE id = ?",
                users.findById(inactiveCompany).orElseThrow().companyId().value());
        UserInvitationMailEventHandler target = AopTestUtils.getTargetObject(handler);

        for (UserId user : List.of(control, suspended, inactiveCompany)) {
            transactionTemplate.executeWithoutResult(
                    status -> target.on(new UserRegistered(UUID.randomUUID(), user, new UtcInstant(NOW))));
        }

        assertThat(tokenCount(control)).as("有効な利用者には発行する（対照）").isEqualTo(1);
        assertThat(tokenCount(suspended)).isZero();
        assertThat(tokenCount(inactiveCompany)).isZero();
        assertThat(mailpit()
                        .messagesTo(users.findById(inactiveCompany)
                                .orElseThrow()
                                .email()
                                .value()))
                .isEmpty();
        assertThat(mailpit()
                        .messagesTo(
                                users.findById(suspended).orElseThrow().email().value()))
                .isEmpty();
    }

    @Test
    void 登録から24時間を過ぎて遅れて配られたDE25では招待を発行しない() {
        // Bolt 38b4 の行 9（Bolt 38b2c の議題 5。再起動の後の配り直し）
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        UserId control = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        UserInvitationMailEventHandler target = AopTestUtils.getTargetObject(handler);

        transactionTemplate.executeWithoutResult(status -> target.on(
                new UserRegistered(UUID.randomUUID(), user, new UtcInstant(NOW.minus(Duration.ofHours(24))))));
        transactionTemplate.executeWithoutResult(status -> target.on(new UserRegistered(
                UUID.randomUUID(),
                control,
                new UtcInstant(NOW.minus(Duration.ofHours(24)).plusSeconds(1)))));

        assertThat(tokenCount(control)).as("24 時間の前の登録には発行する（対照）").isEqualTo(1);
        assertThat(tokenCount(user)).isZero();
    }

    @Test
    void 登録の後に本人の再設定のトークンがあれば招待を発行せず再設定のリンクを無効にしない() {
        // Bolt 38b4 の行 10（後なら譲る、同じ時刻なら発行する）
        UserId later = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        UserId same = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        Instant registered = NOW.minus(Duration.ofHours(2));
        String laterHash = sha256("reset-" + UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> {
            tokens.add(PasswordResetToken.issue(
                    PasswordResetTokenPurpose.RESET,
                    UUID.randomUUID(),
                    later,
                    laterHash,
                    new UtcInstant(NOW.minusSeconds(60))));
            tokens.add(PasswordResetToken.issue(
                    PasswordResetTokenPurpose.RESET,
                    UUID.randomUUID(),
                    same,
                    sha256("reset-" + UUID.randomUUID()),
                    new UtcInstant(registered)));
        });
        UserInvitationMailEventHandler target = AopTestUtils.getTargetObject(handler);

        transactionTemplate.executeWithoutResult(
                status -> target.on(new UserRegistered(UUID.randomUUID(), later, new UtcInstant(registered))));
        transactionTemplate.executeWithoutResult(
                status -> target.on(new UserRegistered(UUID.randomUUID(), same, new UtcInstant(registered))));

        assertThat(tokenCount(later)).isEqualTo(1);
        assertThat(tokens.findByTokenHash(laterHash).orElseThrow().invalidatedAt())
                .isEmpty();
        assertThat(tokenCount(same)).as("同じ時刻の再設定には譲らない").isEqualTo(2);
    }
}
