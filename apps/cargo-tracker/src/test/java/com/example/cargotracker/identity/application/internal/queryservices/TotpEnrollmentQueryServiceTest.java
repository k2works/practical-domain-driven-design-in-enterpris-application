package com.example.cargotracker.identity.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.acceptance.InMemoryTotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TOTP の登録の問い合わせ（Bolt 31b。TotpVerificationService から移した）。 */
class TotpEnrollmentQueryServiceTest {

    @Test
    void 資格のある利用者だけを登録済みとする() {
        InMemoryTotpCredentialRepository credentials = new InMemoryTotpCredentialRepository();
        UserId enrolled = new UserId(UUID.randomUUID());
        credentials.add(TotpCredential.enroll(
                enrolled, new EncryptedSecret("v1:ciphertext"), new UtcInstant(Instant.ofEpochSecond(0))));
        TotpEnrollmentQueryService service = new TotpEnrollmentQueryService(credentials);

        assertThat(service.isEnrolled(enrolled)).isTrue();
        assertThat(service.isEnrolled(new UserId(UUID.randomUUID()))).isFalse();
    }
}
