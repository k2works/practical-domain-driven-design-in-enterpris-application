package com.example.cargotracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Mailpit の SMTP のポート。 */
    public static final int MAILPIT_SMTP_PORT = 1025;

    /** Mailpit の HTTP の API のポート（届いたメールを読む）。 */
    public static final int MAILPIT_HTTP_PORT = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
                // アプリケーション利用者を作り、Flyway の afterMigrate が権限を与えられるようにする（DA-02）
                .withInitScript("db/testcontainers/init-app-user.sql");
    }

    /**
     * メールを受けるだけのコンテナ（Mailpit。テスト戦略、ADR-017。Bolt 33）。版は公開から 2 週間以上たった版に固定する。
     * 同じ組み立てのテストはコンテナを共有するので、届いたメールは宛先で絞って確かめる。
     */
    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.3"))
                .withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_HTTP_PORT)
                .waitingFor(Wait.forHttp("/livez").forPort(MAILPIT_HTTP_PORT));
    }

    /**
     * アプリのメールの送信の接続先を Mailpit にする。Boot の自動構成は {@code spring.mail.host} の有無を部品の登録の前に判定するので、
     * コンテナの起動の後に決まる接続先は、送信の部品を直接組み立てて渡す。
     */
    @Bean
    JavaMailSender mailpitMailSender(GenericContainer<?> mailpitContainer) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailpitContainer.getHost());
        sender.setPort(mailpitContainer.getMappedPort(MAILPIT_SMTP_PORT));
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }
}
