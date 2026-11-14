package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.identity.application.internal.eventhandlers.ShipperNotificationEventHandler;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.quotation.domain.events.QuotationExpiring;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;

/**
 * 荷主担当者の通知（メール）と C-16 通知一覧（US-22 AC1〜AC4。Bolt 43-3）の画面の層のステップ定義。契機は、各コンテキストが発行するイベントを受ける入口
 * （identity のハンドラー）へテストの準備として与える。メールは Mailpit で確かめる。
 */
public class ShipperNotificationUiSteps {

    private static final Duration MAIL_WAIT = Duration.ofSeconds(10);
    private static final Instant NOW_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final UtcInstant AT = new UtcInstant(NOW_AT);
    private static final String REQUEST_NUMBER = "TR-2026-0101";

    private final BrowserSession browser;
    private final UiUsers users;
    private final ShipperNotificationEventHandler handler;
    private final NotificationRepository notifications;
    private final GenericContainer<?> mailpitContainer;
    private final String baseUrl;

    @Autowired
    public ShipperNotificationUiSteps(
            BrowserSession browser,
            UiUsers users,
            ShipperNotificationEventHandler handler,
            NotificationRepository notifications,
            GenericContainer<?> mailpitContainer,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.handler = handler;
        this.notifications = notifications;
        this.mailpitContainer = mailpitContainer;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private CompanyId company() {
        return users.companyOf(Role.SHIPPER);
    }

    @もし("荷主の企業の本予約が確定したイベントを受ける")
    public void 本予約が確定した() {
        handler.on(new BookingConfirmed(
                UUID.randomUUID(),
                1,
                "CTUI"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 10)
                                .toUpperCase(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                REQUEST_NUMBER,
                "RC-2026-0101",
                1,
                AT,
                1L,
                company(),
                null));
    }

    @もし("荷主の企業の見積りが提示されたイベントを受ける")
    public void 見積りが提示された() {
        handler.on(new QuotationPresented(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                1,
                AT,
                List.of("SGSIN"),
                AT,
                AT,
                AT,
                company().value(),
                REQUEST_NUMBER));
    }

    @もし("荷主の企業の経路が割り当てられたイベントを受ける")
    public void 経路が割り当てられた() {
        handler.on(new QuotationRouteAssigned(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                1,
                "RC-2026-0101",
                1,
                AT,
                company().value(),
                REQUEST_NUMBER));
    }

    @もし("荷主の企業の追跡が確認中になったイベントを受ける")
    public void 追跡が確認中になった() {
        handler.on(new TrackingPlacedUnderReview(
                "CTUIREVIEW0001",
                UUID.randomUUID(),
                "OUT_OF_SEQUENCE",
                2,
                AT,
                3L,
                company().value()));
    }

    @もし("荷主の企業の見積りの有効期限が 24 時間後に近づいたイベントを受ける")
    public void 有効期限が近い() {
        handler.on(new QuotationExpiring(
                UUID.randomUUID(), 1, UUID.randomUUID(), AT, company().value(), REQUEST_NUMBER));
    }

    @前提("荷主担当者宛ての送信に失敗した通知が記録されている")
    public void 失敗した通知がある() {
        UUID eventId = UUID.randomUUID();
        UUID recipient = users.of(Role.SHIPPER).userId().value();
        Notification pending = Notification.pending(
                eventId,
                recipient,
                users.of(Role.SHIPPER).email(),
                NotificationKind.BOOKING_CONFIRMED,
                REQUEST_NUMBER,
                "ja",
                AT);
        notifications.addIfAbsent(pending);
        notifications.recordOutcome(pending.failed("SMTP に接続できない"));
    }

    @ならば("荷主担当者に件名 {string} のメールが届き本文は導線を含み料金や契約条件を含まない")
    public void メールが届く(String subject) {
        List<ReceivedMail> mails = new MailpitClient(mailpitContainer)
                .awaitMessagesTo(users.of(Role.SHIPPER).email(), 1, MAIL_WAIT);
        assertThat(mails).anySatisfy(mail -> {
            assertThat(mail.subject()).isEqualTo(subject);
            assertThat(mail.text()).contains("/customer/");
            assertThat(mail.text()).doesNotContain("料金").doesNotContain("円").doesNotContain("契約");
        });
    }

    @もし("荷主担当者が通知の一覧を開く")
    public void 一覧を開く() {
        browser.navigate(baseUrl + "/customer/notifications");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("通知"))
                        .first())
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("通知の一覧に {string} が送信結果 {string} で示される")
    public void 一覧に示される(String title, String result) {
        Locator row = page().getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(title))
                .first();
        assertThat(row).isVisible();
        assertThat(row).containsText(result);
        assertThat(page().locator("body")).not().containsText("SMTP に接続できない");
    }

    @ならば("通知の一覧に {string} が送信結果 {string} で示され宛先が示される")
    public void 失敗と宛先が示される(String title, String result) {
        Locator row = page().getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(title))
                .first();
        assertThat(row).containsText(result);
        assertThat(row).containsText("宛先: " + users.of(Role.SHIPPER).email());
        assertThat(page().locator("body")).not().containsText("SMTP に接続できない");
    }

    private Page page() {
        return browser.page();
    }
}
