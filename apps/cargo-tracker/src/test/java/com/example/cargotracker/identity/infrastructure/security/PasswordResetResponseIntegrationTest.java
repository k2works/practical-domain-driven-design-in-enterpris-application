package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.events.PasswordResetRequested;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再設定の依頼の応答はトークンの発行もメールの送信も待たない（登録の有無を応答の時間に出さない。SEC-08、ADR-017。Bolt 33）。送信を 3 秒
 * 遅らせても、依頼の応答は先に返り、メールは後から送られる。送信を依頼のトランザクションの中に戻すと落ちる（T-112）。あわせて、ハンドラーの
 * 経路で送信が失敗しても、例外にせず発行記録を完了にし、トークンを残して通知を失敗にすることを確かめる（Bolt 33 の開発レビュー）。
 * 送信の部品を差し替えるので、このクラスだけのコンテキストになる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PasswordResetResponseIntegrationTest.ControlledMail.class})
class PasswordResetResponseIntegrationTest {

    private static final Duration SEND_DELAY = Duration.ofSeconds(3);

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ControlledMail.Recorder recorder;

    private UserId newUser() {
        return transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
    }

    private String emailOf(UserId user) {
        return users.findById(user).orElseThrow().email().value();
    }

    @Test
    void 依頼の応答は送信を待たずに返りメールは後から送られる() throws Exception {
        String email = emailOf(newUser());
        recorder.delay = SEND_DELAY;
        // 最初の要求の準備（テンプレートの読み込みなど）を計る時間に入れない
        mvc.perform(get("/password-reset")).andExpect(status().isOk());

        long started = System.nanoTime();
        mvc.perform(post("/password-reset").param("email", email).with(csrf()))
                .andExpect(redirectedUrl("/login?reset-requested"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).as("応答は送信の遅れより先に返る").isLessThan(SEND_DELAY.dividedBy(2));
        await().atMost(SEND_DELAY.multipliedBy(4)).until(() -> recorder.sentTo(email));
    }

    @Test
    void ハンドラーの経路で送信が失敗しても発行記録を完了にしトークンを残して通知を失敗にする() throws Exception {
        UserId user = newUser();
        String email = emailOf(user);
        recorder.delay = Duration.ZERO;
        recorder.failing.add(email);

        mvc.perform(post("/password-reset").param("email", email).with(csrf()))
                .andExpect(redirectedUrl("/login?reset-requested"));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                                Integer.class,
                                PasswordResetRequested.class.getName(),
                                "%" + user.value() + "%")
                        == 1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?",
                        Integer.class,
                        user.value()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM notification.notification WHERE recipient_user_id = ?",
                        String.class,
                        user.value()))
                .isEqualTo("FAILED");
        assertThat(recorder.sentTo(email)).isFalse();
    }

    /** 遅れと失敗を指定できる送信（テスト用）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledMail {

        /** 送った宛先と、遅れ・失敗の指定。 */
        static final class Recorder {
            private final List<String> recipients = new CopyOnWriteArrayList<>();
            private final List<String> failing = new CopyOnWriteArrayList<>();
            private volatile Duration delay = Duration.ZERO;

            boolean sentTo(String address) {
                return recipients.contains(address);
            }
        }

        @Bean
        Recorder controlledMailRecorder() {
            return new Recorder();
        }

        @Bean
        @Primary
        MailSender controlledMailSender(Recorder recorder) {
            return (OutgoingMail mail) -> {
                wait(recorder.delay);
                if (recorder.failing.contains(mail.to())) {
                    throw new IllegalStateException("送れない（テスト）");
                }
                recorder.recipients.add(mail.to());
            };
        }

        /** 遅れを待つ（数えられないラッチを指定の時間だけ待つ）。 */
        private static void wait(Duration delay) {
            try {
                if (new CountDownLatch(1).await(delay.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("数えないラッチが開いた");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
