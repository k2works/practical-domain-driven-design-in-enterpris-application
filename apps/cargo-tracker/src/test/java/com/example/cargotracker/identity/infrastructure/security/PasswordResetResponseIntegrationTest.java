package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再設定の依頼の応答はトークンの発行もメールの送信も待たない（登録の有無を応答の時間に出さない。SEC-08、ADR-017。Bolt 33）。送信を 3 秒
 * 遅らせても、依頼の応答は先に返り、メールは後から送られる。送信を依頼のトランザクションの中に戻すと落ちる（T-112）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PasswordResetResponseIntegrationTest.SlowMail.class})
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
    SlowMail.Recorder recorder;

    @Test
    void 依頼の応答は送信を待たずに返りメールは後から送られる() throws Exception {
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        String email = users.findById(user).orElseThrow().email().value();

        long started = System.nanoTime();
        mvc.perform(post("/password-reset").param("email", email).with(csrf()))
                .andExpect(redirectedUrl("/login?reset-requested"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).as("応答は送信の遅れより先に返る").isLessThan(SEND_DELAY.dividedBy(2));
        await().atMost(SEND_DELAY.multipliedBy(4)).until(() -> recorder.sentTo(email));
    }

    /** 送信を遅らせ、送った宛先を記録する送信（テスト用）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SlowMail {

        /** 送った宛先。 */
        static final class Recorder {
            private final List<String> recipients = new CopyOnWriteArrayList<>();

            boolean sentTo(String address) {
                return recipients.contains(address);
            }
        }

        @Bean
        Recorder slowMailRecorder() {
            return new Recorder();
        }

        @Bean
        @Primary
        MailSender slowMailSender(Recorder recorder) {
            return (OutgoingMail mail) -> {
                try {
                    Thread.sleep(SEND_DELAY.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                recorder.recipients.add(mail.to());
            };
        }
    }
}
