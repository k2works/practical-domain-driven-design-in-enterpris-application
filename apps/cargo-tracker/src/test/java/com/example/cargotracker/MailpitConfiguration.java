package com.example.cargotracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * メールを受けるだけのコンテナ（Mailpit。テスト戦略、ADR-017。Bolt 33）。メールを確かめるテスト・identity の統合テスト・画面の層の
 * テストだけが {@code @Import} する（ほかのテストのコンテキストではコンテナを起動しない。読み込まないテストでは SMTP の設定がなく、送ると
 * 送信の失敗として記録される）。版は公開から 2 週間以上たった版に固定する。同じ組み立てのテストはコンテナを共有するので、届いたメールは
 * 宛先で絞って確かめる。
 */
@TestConfiguration(proxyBeanMethods = false)
public class MailpitConfiguration {

    /** Mailpit の SMTP のポート。 */
    public static final int SMTP_PORT = 1025;

    /** Mailpit の HTTP の API のポート（届いたメールを読む）。 */
    public static final int HTTP_PORT = 8025;

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.3"))
                .withExposedPorts(SMTP_PORT, HTTP_PORT)
                .waitingFor(Wait.forHttp("/livez").forPort(HTTP_PORT));
    }

    /**
     * アプリのメールの送信の接続先を Mailpit にする。Boot の自動構成は {@code spring.mail.host} の有無を部品の登録の前に判定するので、
     * コンテナの起動の後に決まる接続先は、送信の部品を直接組み立てて渡す。
     */
    @Bean
    JavaMailSender mailpitMailSender(GenericContainer<?> mailpitContainer) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailpitContainer.getHost());
        sender.setPort(mailpitContainer.getMappedPort(SMTP_PORT));
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }
}
