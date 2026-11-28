package com.example.cargotracker.identity.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.acceptance.InMemoryRecoveryCodeRepository;
import com.example.cargotracker.identity.acceptance.InMemoryTotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.RemainingRecoveryCodes;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** TOTP の登録の問い合わせ（Bolt 31b。TotpVerificationService から移した）。 */
class TotpEnrollmentQueryServiceTest {

    @Test
    void 資格のある利用者だけを登録済みとする() {
        InMemoryTotpCredentialRepository credentials = new InMemoryTotpCredentialRepository();
        UserId enrolled = new UserId(UUID.randomUUID());
        credentials.add(TotpCredential.enroll(
                enrolled, new EncryptedSecret("v1:ciphertext"), new UtcInstant(Instant.ofEpochSecond(0))));
        TotpEnrollmentQueryService service =
                new TotpEnrollmentQueryService(credentials, new InMemoryRecoveryCodeRepository());

        assertThat(service.isEnrolled(enrolled)).isTrue();
        assertThat(service.isEnrolled(new UserId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void 残りの回復コードは受け取った利用者の未使用の一覧の数をそのまま値にする() {
        // Bolt 38d2 の行 2。使用済みを除くのはリポジトリの契約（AuthenticationFactorRepositoriesContract）
        InMemoryRecoveryCodeRepository codes = new InMemoryRecoveryCodeRepository();
        UserId user = new UserId(UUID.randomUUID());
        UserId other = new UserId(UUID.randomUUID());
        UtcInstant at = new UtcInstant(Instant.ofEpochSecond(0));
        List<RecoveryCode> issued = issue(user, at);
        codes.replaceAll(user, issued);
        codes.replaceAll(other, issue(other, at));
        for (int i = 0; i < 7; i++) {
            codes.use(user, issued.get(i).id(), at);
        }
        TotpEnrollmentQueryService service =
                new TotpEnrollmentQueryService(new InMemoryTotpCredentialRepository(), codes);

        assertThat(service.remainingRecoveryCodes(user)).isEqualTo(new RemainingRecoveryCodes(3));
    }

    private static List<RecoveryCode> issue(UserId userId, UtcInstant at) {
        RecoveryCodeHasher hasher = new RecoveryCodeHasher() {
            @Override
            public String hash(String code) {
                return "h:" + code;
            }

            @Override
            public boolean matches(String code, String hash) {
                return hash.equals(hash(code));
            }
        };
        return IntStream.range(0, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(userId, String.format("0000-%04x", i), hasher, at))
                .toList();
    }
}
