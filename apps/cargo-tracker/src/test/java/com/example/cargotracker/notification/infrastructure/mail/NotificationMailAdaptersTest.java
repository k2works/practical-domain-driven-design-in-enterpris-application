package com.example.cargotracker.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

/** メールの文面・ログに出す送信・守り（ADR-017。Bolt 33）。 */
class NotificationMailAdaptersTest {

    private static MockEnvironment profiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void 文面はUI設計の件名と本文でリンクと期限を入れる() {
        OutgoingMail mail = new ThymeleafPasswordResetMailTemplate()
                .compose(
                        "shipper@example.com",
                        "https://cargo.example/password-reset/abc?x=1&y=2",
                        new UtcInstant(Instant.parse("2026-10-15T01:30:00Z")),
                        "ja");

        assertThat(mail.to()).isEqualTo("shipper@example.com");
        assertThat(mail.subject()).isEqualTo("【cargo-tracker】password の再設定のご案内");
        assertThat(mail.body())
                .startsWith("cargo-tracker で、このメールアドレスの password の再設定の依頼を受け付けました。")
                .contains("https://cargo.example/password-reset/abc?x=1&y=2")
                .contains("このリンクは 2026-10-15 10:30 Asia/Tokyo（UTC+09:00） まで使えます（30 分間、1 回だけ）。")
                .contains("リンクを開かなければ、password は変わりません。")
                .doesNotContain("&amp;");
    }

    @Test
    void 利用者の招待の文面はUI設計の件名と6文でリンクと期限と出口を入れ業務の情報を書かない() {
        // 行 5（Bolt 38b2b。N-INV-01）
        OutgoingMail mail = new ThymeleafUserInvitationMailTemplate()
                .compose(
                        "hanako@example.com",
                        "https://cargo.example/password-reset/abc?x=1&y=2",
                        new UtcInstant(Instant.parse("2026-10-27T01:30:00Z")),
                        "ja");

        assertThat(mail.to()).isEqualTo("hanako@example.com");
        assertThat(mail.subject()).isEqualTo("【cargo-tracker】利用者の登録のご案内");
        assertThat(mail.body())
                .startsWith("cargo-tracker の利用者として、このメールアドレスが登録されました。")
                .contains("次のリンクを開いて、password を設定してください。")
                .contains("https://cargo.example/password-reset/abc?x=1&y=2")
                .contains("このリンクは 2026-10-27 10:30 Asia/Tokyo（UTC+09:00） まで、1 回だけ使えます。")
                .contains("期限を過ぎたときは、ログインの画面の「password を忘れた場合」から設定できます。")
                .contains("心当たりがない場合は、このメールを無視してください。")
                .doesNotContain("&amp;")
                .doesNotContain("役割")
                .doesNotContain("企業");
        assertThat(mail.body().lines()).hasSize(6);
    }

    @Test
    void 解除のお知らせはUI設計の件名と4文で役割と日時を入れ本人確認の手段や操作した人を書かない() {
        // Bolt 38d の行 10（N-INV-01。解除のお知らせだけ役割を書く）
        OutgoingMail mail = new ThymeleafTotpResetMailTemplate()
                .compose("hanako@example.com", new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")), "ja");

        assertThat(mail.to()).isEqualTo("hanako@example.com");
        assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの登録の解除のご案内");
        assertThat(mail.body()).isEqualTo("""
                        cargo-tracker で、このメールアドレスの利用者の認証アプリの登録を、システム管理者が 2026-11-09 10:30 Asia/Tokyo（UTC+09:00） に解除しました。
                        回復コードも使えなくなりました。
                        次のログインで、認証アプリを登録し直してください。
                        この解除に心当たりがない場合は、荷主の方は担当営業に、社内の方はシステム管理者にお知らせください。
                        """);
    }

    @Test
    void 登録のお知らせはUI設計の件名と2文で日時と連絡先を入れ誰が登録したかを書かない() {
        // Bolt 38d の行 10
        OutgoingMail mail = new ThymeleafTotpEnrollmentMailTemplate()
                .compose("hanako@example.com", new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")), false, "ja");

        assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの登録のご案内");
        assertThat(mail.body()).isEqualTo("""
                        cargo-tracker で、このメールアドレスの利用者の認証アプリが、2026-11-09 10:30 Asia/Tokyo（UTC+09:00） に登録されました。
                        この登録に心当たりがない場合は、すぐに、荷主の方は担当営業に、社内の方はシステム管理者にお知らせください。
                        """);
    }

    @Test
    void 再登録のお知らせはUI設計の件名と3文で前の要素が使えないことを入れ回復コードの値を書かない() {
        // Bolt 38d の行 10
        OutgoingMail mail = new ThymeleafTotpEnrollmentMailTemplate()
                .compose("hanako@example.com", new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")), true, "ja");

        assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの再登録のご案内");
        assertThat(mail.body()).isEqualTo("""
                        cargo-tracker で、このメールアドレスの利用者の認証アプリが、2026-11-09 10:30 Asia/Tokyo（UTC+09:00） に登録し直されました。
                        回復コードでログインして登録し直したため、前の認証アプリと前の回復コードは使えなくなりました。
                        この操作に心当たりがない場合は、すぐに、荷主の方は担当営業に、社内の方はシステム管理者にお知らせください。
                        """);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void ログに出す送信は宛先と件名と本文を出す(CapturedOutput output) {
        new LoggingMailSender().send(new OutgoingMail("shipper@example.com", "件名", "本文のリンク"));

        assertThat(output.getOut())
                .contains("宛先: shipper@example.com")
                .contains("件名: 件名")
                .contains("本文のリンク");
    }

    @Test
    void ログに出す送信はdevだけで使う() {
        assertThat(NotificationMailGuard.logsMail(profiles("dev"))).isTrue();
        assertThat(NotificationMailGuard.logsMail(profiles())).isFalse();
        assertThat(NotificationMailGuard.logsMail(profiles("dev", "staging"))).isFalse();
    }

    @Test
    void devとstagingやprodの併用では起動を止める() {
        MockEnvironment dev = profiles("dev");
        MockEnvironment devAndStaging = profiles("dev", "staging");
        MockEnvironment devAndProd = profiles("dev", "prod");

        assertThatCode(() -> NotificationMailGuard.check(dev)).doesNotThrowAnyException();
        assertThatThrownBy(() -> NotificationMailGuard.check(devAndStaging)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NotificationMailGuard.check(devAndProd)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ステージングと本番ではSMTPの設定がなければ起動を止める() {
        MockEnvironment withSmtp = profiles("prod");
        withSmtp.setProperty("spring.mail.host", "email-smtp.ap-northeast-1.amazonaws.com");
        MockEnvironment staging = profiles("staging");
        MockEnvironment prod = profiles("prod");
        MockEnvironment none = profiles();

        assertThatCode(() -> NotificationMailGuard.check(withSmtp)).doesNotThrowAnyException();
        assertThatThrownBy(() -> NotificationMailGuard.check(staging)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NotificationMailGuard.check(prod)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> NotificationMailGuard.check(none))
                .as("テストなどのプロファイルなしは SMTP の設定がなくても起動する（送ると失敗を記録する）")
                .doesNotThrowAnyException();
    }
}
