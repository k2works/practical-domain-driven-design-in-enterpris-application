package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.acceptance.InMemoryTotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TOTP の確かめ（Bolt 31、US-18 AC1、IA-INV-10）。前後 1 つの刻みの照合と、通った刻みの条件付きの使用。
 * 拒否の種類は監査記録の理由にだけ使い、利用者には同じ文言を示す（AC2）。
 */
class TotpVerificationServiceTest {

    /** 今の刻みは 100（epoch 3000 秒 / 30）。 */
    private static final Instant NOW = Instant.ofEpochSecond(3_000);

    /** 偽の生成器。秘密を使わず、刻みの番号を 6 桁にしたものをコードにする。 */
    private static final TotpCodeGenerator GENERATOR = (owner, secret, step) -> String.format("%06d", step % 1_000_000);

    private final InMemoryTotpCredentialRepository credentials = new InMemoryTotpCredentialRepository();
    private final TotpVerificationService service =
            new TotpVerificationService(credentials, GENERATOR, Clock.fixed(NOW, ZoneOffset.UTC));
    private UserId user;

    @BeforeEach
    void enroll() {
        user = new UserId(UUID.randomUUID());
        credentials.add(TotpCredential.enroll(
                user, new EncryptedSecret("v1:ciphertext"), new UtcInstant(Instant.ofEpochSecond(0))));
    }

    @Test
    void 今の刻みと前後1つの刻みのコードは通る() {
        assertThat(service.verify(user, "000099")).isEqualTo(TotpVerification.ACCEPTED);
        assertThat(service.verify(user, "000100")).isEqualTo(TotpVerification.ACCEPTED);
        assertThat(service.verify(user, "000101")).isEqualTo(TotpVerification.ACCEPTED);
    }

    @Test
    void 二つ離れた刻みのコードは一致しないとして拒む() {
        assertThat(service.verify(user, "000098")).isEqualTo(TotpVerification.REJECTED_MISMATCH);
        assertThat(service.verify(user, "000102")).isEqualTo(TotpVerification.REJECTED_MISMATCH);
        assertThat(service.verify(user, "abcdef")).isEqualTo(TotpVerification.REJECTED_MISMATCH);
    }

    @Test
    void 通った刻みを記録し同じコードの2回目は再利用として拒む() {
        assertThat(service.verify(user, "000100")).isEqualTo(TotpVerification.ACCEPTED);

        assertThat(credentials.findByUserId(user).orElseThrow().lastUsedStep()).isEqualTo(100);
        assertThat(service.verify(user, "000100")).isEqualTo(TotpVerification.REJECTED_REUSED);
        assertThat(service.verify(user, "000099")).as("使った刻みより前").isEqualTo(TotpVerification.REJECTED_REUSED);
        assertThat(service.verify(user, "000101")).as("使った刻みより後").isEqualTo(TotpVerification.ACCEPTED);
    }

    @Test
    void 拒否のときも生成の呼び出しは窓の刻みの数だけで資格を作り直さない() {
        // H1: 照合の結果（TotpMatch）を写すだけにし、再利用を見分けるための 2 回目の照合をしない（Bolt 31d）
        AtomicInteger calls = new AtomicInteger();
        TotpCodeGenerator counting = (owner, secret, step) -> {
            calls.incrementAndGet();
            return GENERATOR.code(owner, secret, step);
        };
        TotpVerificationService countingService =
                new TotpVerificationService(credentials, counting, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(countingService.verify(user, "000098")).isEqualTo(TotpVerification.REJECTED_MISMATCH);
        assertThat(calls).as("不一致").hasValue(3);
        countingService.verify(user, "000100");
        calls.set(0);
        assertThat(countingService.verify(user, "000100")).isEqualTo(TotpVerification.REJECTED_REUSED);
        assertThat(calls).as("再利用").hasValue(3);
    }

    @Test
    void 資格のない利用者は拒む() {
        UserId notEnrolled = new UserId(UUID.randomUUID());

        assertThat(service.verify(notEnrolled, "000100")).isEqualTo(TotpVerification.REJECTED_NOT_ENROLLED);
    }
}
