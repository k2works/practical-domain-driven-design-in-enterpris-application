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
