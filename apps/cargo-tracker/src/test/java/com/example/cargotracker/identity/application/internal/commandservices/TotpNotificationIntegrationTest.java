package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.application.internal.eventhandlers.TotpEnrollmentMailEventHandler;
import com.example.cargotracker.identity.application.internal.eventhandlers.TotpResetMailEventHandler;
import com.example.cargotracker.identity.domain.events.TotpEnrolled;
import com.example.cargotracker.identity.domain.events.TotpReEnrolled;
import com.example.cargotracker.identity.domain.events.TotpResetByAdmin;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * TOTP の解除・登録・再登録から、コミットの後のお知らせのメールまで（US-18 AC9、SEC-10、ADR-017。Bolt 38d の行 1〜3・6・11・14）。本物の
 * PostgreSQL と Mailpit で確かめる。同じコンテキストを共有するので、記録は利用者とメールアドレスで絞る（T-113）。
 */
@IdentityIntegrationTest
class TotpNotificationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:40:00Z");
    private static final Instant ENROLLED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final UtcInstant VERIFIED_AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    UserLoginCredentialService credentials;

    @Autowired
    TotpResetMailEventHandler resetHandler;

    @Autowired
    TotpEnrollmentMailEventHandler enrollmentHandler;

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

    private User newSales() {
        UserId id = new UserId(UUID.randomUUID());
        User sales = User.of(
                id,
                operator.id(),
                EmailAddress.of("totp-" + id.value() + "@example.com"),
                "営業 一郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES));
        transactionTemplate.executeWithoutResult(status -> users.add(sales));
        return sales;
    }

    private void enroll(UserId user) {
        jdbc.update(
                "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                        + " VALUES (?, 'ciphertext', 0, ?)",
                user.value(),
                Timestamp.from(ENROLLED_AT));
    }

    private MailpitClient mailpit() {
        return new MailpitClient(mailpitContainer);
    }

    /** その利用者の監査記録の ID（操作で絞る）。 */
    private UUID auditRecordOf(UserId user, String action) {
        return jdbc.queryForObject(
                "SELECT id FROM identity.audit_record WHERE target_id = ? AND action = ?",
                UUID.class,
                user.value().toString(),
                action);
    }

    /** そのイベントの型と利用者の発行の記録。 */
    private List<String> publicationsOf(Class<?> eventType, UserId user) {
        return jdbc.queryForList(
                "SELECT serialized_event FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?",
                String.class,
                eventType.getName(),
                "%" + user.value() + "%");
    }

    /** その利用者の発行の記録が完了するまで待つ（ハンドラーが動き終えた印）。 */
    private void awaitCompletedPublication(Class<?> eventType, UserId user) {
        await().atMost(WAIT)
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                                Integer.class,
                                eventType.getName(),
                                "%" + user.value() + "%")
                        == 1);
    }

    private Map<String, Object> notificationOf(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT kind, status, target_ref, recipient_user_id FROM notification.notification WHERE event_id = ?",
                eventId);
    }

    @Test
    void 管理者が解除するとコミットの後に解除のお知らせが本人に1通届き監査記録のIDで記録する() {
        // 行 1
        User sales = newSales();
        enroll(sales.id());

        assertThat(credentials.resetTotp(
                        admin,
                        sales.id(),
                        new UtcInstant(ENROLLED_AT),
                        IdentityVerificationMethod.IN_PERSON,
                        VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Reset());

        List<ReceivedMail> mails = mailpit().awaitMessagesTo(sales.email().value(), 1, WAIT);
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの登録の解除のご案内");
            assertThat(mail.text()).contains("システム管理者が 2026-11-09 10:40 Asia/Tokyo（UTC+09:00） に解除しました。");
        });
        awaitCompletedPublication(TotpResetByAdmin.class, sales.id());
        UUID auditRecordId = auditRecordOf(sales.id(), "TOTP_RESET_BY_ADMIN");
        assertThat(notificationOf(auditRecordId))
                .containsEntry("kind", "TOTP_RESET_BY_ADMIN")
                .containsEntry("status", "SENT")
                .containsEntry("target_ref", auditRecordId.toString())
                .containsEntry("recipient_user_id", sales.id().value());
    }

    @Test
    void 本文の日時はハンドラーが動いた時刻ではなく解除の時刻を示す() {
        // 行 14（変異 V10。遅れた配信でもずれない）
        User sales = newSales();
        TotpResetMailEventHandler target = AopTestUtils.getTargetObject(resetHandler);
        TotpResetByAdmin event = new TotpResetByAdmin(
                UUID.randomUUID(), sales.id(), new UtcInstant(Instant.parse("2026-11-08T23:15:00Z")));
        clock.setInstant(NOW.plus(Duration.ofHours(3)));

        transactionTemplate.executeWithoutResult(status -> target.on(event));

        assertThat(mailpit().awaitMessagesTo(sales.email().value(), 1, WAIT))
                .singleElement()
                .satisfies(mail ->
                        assertThat(mail.text()).contains("システム管理者が 2026-11-09 08:15 Asia/Tokyo（UTC+09:00） に解除しました。"));
    }

    @Test
    void 同じ解除のイベントの再配信では2通目を送らない() {
        // 行 6（変異 V6。N-INV-02。1 回目が確定した場合）
        User sales = newSales();
        TotpResetMailEventHandler target = AopTestUtils.getTargetObject(resetHandler);
        TotpResetByAdmin event = new TotpResetByAdmin(UUID.randomUUID(), sales.id(), new UtcInstant(NOW));

        transactionTemplate.executeWithoutResult(status -> target.on(event));
        transactionTemplate.executeWithoutResult(status -> target.on(event));

        assertThat(mailpit().awaitMessagesTo(sales.email().value(), 1, WAIT)).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification.notification WHERE event_id = ?",
                        Integer.class,
                        event.auditRecordId()))
                .isEqualTo(1);
    }

    @Test
    void メールアドレスは解除の発行記録とログに残らない() {
        // 行 11（前例 UserInvitationIntegrationTest。ログはハンドラーが動き終えるまで取り込む）
        User sales = newSales();
        enroll(sales.id());
        try (LogCapture logs = LogCapture.start()) {
            credentials.resetTotp(
                    admin, sales.id(), new UtcInstant(ENROLLED_AT), IdentityVerificationMethod.IN_PERSON, VERIFIED_AT);
            mailpit().awaitMessagesTo(sales.email().value(), 1, WAIT);
            awaitCompletedPublication(TotpResetByAdmin.class, sales.id());

            assertThat(logs.text()).doesNotContain(sales.email().value());
        }
        assertThat(publicationsOf(TotpResetByAdmin.class, sales.id()))
                .singleElement()
                .asString()
                .doesNotContain(sales.email().value())
                .doesNotContain("IN_PERSON")
                .doesNotContain(admin.userId().value().toString());
    }

    @Test
    void 登録と再登録のイベントで本人に登録と再登録のお知らせが届き監査記録のIDで1通ずつ記録する() {
        // 行 2・3・6（登録のサービスがイベントを出すことはサービスの単体と受入シナリオで確かめる）
        User sales = newSales();
        TotpEnrollmentMailEventHandler target = AopTestUtils.getTargetObject(enrollmentHandler);
        TotpEnrolled enrolled = new TotpEnrolled(UUID.randomUUID(), sales.id(), new UtcInstant(NOW));
        TotpReEnrolled reEnrolled = new TotpReEnrolled(UUID.randomUUID(), sales.id(), new UtcInstant(NOW));

        transactionTemplate.executeWithoutResult(status -> target.on(enrolled));
        transactionTemplate.executeWithoutResult(status -> target.on(reEnrolled));
        transactionTemplate.executeWithoutResult(status -> target.on(reEnrolled));

        assertThat(mailpit().awaitMessagesTo(sales.email().value(), 2, WAIT))
                .extracting(ReceivedMail::subject)
                .containsExactlyInAnyOrder("【cargo-tracker】認証アプリの登録のご案内", "【cargo-tracker】認証アプリの再登録のご案内");
        assertThat(notificationOf(enrolled.auditRecordId())).containsEntry("kind", "TOTP_ENROLLED");
        assertThat(notificationOf(reEnrolled.auditRecordId()))
                .containsEntry("kind", "TOTP_RE_ENROLLED")
                .containsEntry("target_ref", reEnrolled.auditRecordId().toString());
    }
}
