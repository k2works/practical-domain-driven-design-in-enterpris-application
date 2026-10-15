package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * password の再設定の依頼（A-01 のリンク、A-05、A-01 の知らせ）の画面の層のステップ（US-18 AC8 の前半。Bolt 33）。届いたメールは
 * Mailpit で宛先ごとに確かめる。
 */
public class PasswordResetUiSteps {

    private static final Duration MAIL_WAIT = Duration.ofSeconds(10);
    private static final Pattern RESET_LINK = Pattern.compile("https?://[^/\\s]+(/password-reset/[A-Za-z0-9_-]{43})");
    private static final String NOTICE = "password の再設定の依頼を受け付けました。";

    private final BrowserSession browser;
    private final UiUsers users;
    private final GenericContainer<?> mailpitContainer;
    private final String baseUrl;
    private final String unknownAddress = "unknown-" + UUID.randomUUID() + "@ui.example";
    private final JdbcTemplate jdbc;

    /** 新しい password（A-06 で設定し、ログインに使う）。 */
    private static final String NEW_PASSWORD = "a brand new long passphrase";

    public PasswordResetUiSteps(
            BrowserSession browser,
            UiUsers users,
            GenericContainer<?> mailpitContainer,
            JdbcTemplate jdbc,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.mailpitContainer = mailpitContainer;
        this.jdbc = jdbc;
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

    private String shipperEmail() {
        return users.of(Role.SHIPPER).email();
    }

    /**
     * 届いたメールのリンクの path（Bolt 33a）。メールは公開の base URL（テストでは既定の localhost:8080）で組み立てるので、path だけを
     * 取り出してテストのサーバーで開く。メールはハンドラーの確定の前に届くので、トークンが確定するまで待つ（T-127）。
     */
    private String resetLinkPath() {
        String text = new MailpitClient(mailpitContainer)
                .awaitMessagesTo(shipperEmail(), 1, MAIL_WAIT)
                .getFirst()
                .text();
        Matcher matcher = RESET_LINK.matcher(text);
        assertThat(matcher.find()).as("本文に再設定のリンクがある").isTrue();
        // 待ちは別のスレッドで動くので、シナリオの範囲の部品（UiUsers）は先に読んでおく
        UUID userId = users.of(Role.SHIPPER).userId().value();
        await().atMost(MAIL_WAIT)
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?",
                                Integer.class,
                                userId)
                        > 0);
        return matcher.group(1);
    }

    @もし("届いたメールの再設定のリンクを開く")
    public void 届いたメールの再設定のリンクを開く() {
        page().navigate(baseUrl + resetLinkPath());
    }

    @もし("再設定のリンクの有効期限が過ぎる")
    public void 再設定のリンクの有効期限が過ぎる() {
        resetLinkPath();
        // サーバーの時計は進められないので、発行の時刻と期限をそろって 1 時間前にする（テストの準備）
        jdbc.update(
                "UPDATE identity.password_reset_token SET issued_at = issued_at - INTERVAL '1 hour',"
                        + " expires_at = expires_at - INTERVAL '1 hour' WHERE user_id = ?",
                users.of(Role.SHIPPER).userId().value());
    }

    @かつ("再設定の画面で新しい password を 2 回入れて設定する")
    public void 新しいpasswordを2回入れて設定する() {
        設定する(NEW_PASSWORD);
    }

    @かつ("再設定の画面で {string} を 2 回入れて設定する")
    public void 設定する(String password) {
        page().waitForURL(url -> url.endsWith("/password-reset/new"));
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("password の再設定")))
                .isVisible();
        browser.checkAccessibility();
        page().getByLabel("新しい password", new Page.GetByLabelOptions().setExact(true))
                .fill(password);
        page().getByLabel("新しい password（確認）").fill(password);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("password を設定する"))
                .click();
    }

    @ならば("ログインの画面に password を変更した知らせが出る")
    public void passwordを変更した知らせが出る() {
        page().waitForURL(url -> url.contains("/login?reset-completed"));
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("password を変更しました。");
        browser.checkAccessibility();
    }

    @もし("ログインの画面でメールアドレスと新しい password を入れてログインする")
    public void 新しいpasswordでログインする() {
        page().getByLabel("メールアドレス").fill(shipperEmail());
        page().getByLabel("password", new Page.GetByLabelOptions().setExact(true))
                .fill(NEW_PASSWORD);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        page().waitForURL(url -> url.contains("/login/totp"));
    }

    @ならば("再設定のリンクは使えないと案内される")
    public void 再設定のリンクは使えないと案内される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("この再設定のリンクは使えません")))
                .isVisible();
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("password の再設定を依頼し直す")))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("再設定の画面の新しい password の欄に {string} と示される")
    public void 新しいpasswordの欄に示される(String message) {
        assertThat(page().getByLabel("新しい password", new Page.GetByLabelOptions().setExact(true)))
                .hasAccessibleDescription(
                        // 説明は誤りの文言のあとに条件の箇条が続く（aria-describedby の順）
                        PlaywrightText.startingWith(message));
        assertThat(page().getByLabel("新しい password", new Page.GetByLabelOptions().setExact(true)))
                .hasValue("");
        browser.checkAccessibility();
    }
}
