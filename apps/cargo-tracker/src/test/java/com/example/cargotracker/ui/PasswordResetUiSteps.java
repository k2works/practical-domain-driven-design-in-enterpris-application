package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;

/**
 * password の再設定の依頼（A-01 のリンク、A-05、A-01 の知らせ）の画面の層のステップ（US-18 AC8 の前半。Bolt 33）。届いたメールは
 * Mailpit で宛先ごとに確かめる。
 */
public class PasswordResetUiSteps {

    private static final Duration MAIL_WAIT = Duration.ofSeconds(10);
    private static final String NOTICE = "password の再設定の依頼を受け付けました。";

    private final BrowserSession browser;
    private final UiUsers users;
    private final GenericContainer<?> mailpitContainer;
    private final String baseUrl;
    private final String unknownAddress = "unknown-" + UUID.randomUUID() + "@ui.example";

    public PasswordResetUiSteps(
            BrowserSession browser,
            UiUsers users,
            GenericContainer<?> mailpitContainer,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.mailpitContainer = mailpitContainer;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private Locator emailField() {
        return page().getByLabel("メールアドレス");
    }

    @もし("ログインの画面で {string} を開く")
    public void ログインの画面でリンクを開く(String name) {
        page().navigate(baseUrl + "/login");
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name))
                .click();
        page().waitForURL(url -> url.endsWith("/password-reset"));
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("password の再設定の依頼")))
                .isVisible();
        browser.checkAccessibility();
    }

    @かつ("再設定の依頼の画面で登録したメールアドレスを入れて送る")
    public void 登録したメールアドレスを入れて送る() {
        送る(users.of(Role.SHIPPER).email());
    }

    @かつ("再設定の依頼の画面で登録のないメールアドレスを入れて送る")
    public void 登録のないメールアドレスを入れて送る() {
        送る(unknownAddress);
    }

    @かつ("再設定の依頼の画面で {string} を入れて送る")
    public void 送る(String email) {
        emailField().fill(email);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("再設定のリンクを送る"))
                .click();
    }

    @ならば("ログインの画面に再設定の依頼を受け付けた知らせが出る")
    public void ログインの画面に知らせが出る() {
        page().waitForURL(url -> url.contains("/login?reset-requested"));
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(NOTICE);
        browser.checkAccessibility();
    }

    @ならば("登録したメールアドレスに再設定のリンクのメールが届く")
    public void 再設定のリンクのメールが届く() {
        String email = users.of(Role.SHIPPER).email();
        List<ReceivedMail> mails = new MailpitClient(mailpitContainer).awaitMessagesTo(email, 1, MAIL_WAIT);
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】password の再設定のご案内");
            assertThat(mail.text()).containsPattern("/password-reset/[A-Za-z0-9_-]{43}");
        });
    }

    @かつ("登録のないメールアドレスにはメールが届かない")
    public void 登録のないメールアドレスにはメールが届かない() {
        // 同じシナリオで、登録のないアドレスの依頼の後に出した登録したアドレスの依頼が届いたことを先に確かめている（T-119）
        assertThat(new MailpitClient(mailpitContainer).messagesTo(unknownAddress))
                .isEmpty();
    }

    @ならば("再設定の依頼の画面のメールアドレスの欄に {string} と示される")
    public void 欄に誤りが示される(String message) {
        assertThat(emailField()).hasAccessibleDescription(message);
        browser.checkAccessibility();
    }

    @かつ("再設定の依頼の画面のメールアドレスの欄に {string} が残っている")
    public void 欄に値が残っている(String value) {
        assertThat(emailField()).hasValue(value);
    }
}
