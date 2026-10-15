package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TOTP の登録の始め（Bolt 33d で初回の登録と再登録の重複を寄せた）。途中の暗号文は読める限り使い続ける。 */
class TotpProvisioningStarterTest {

    private static final UserId OWNER = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000d001"));
    private static final String ACCOUNT = "shipper@example.com";

    /** 偽の秘密の発行。持ち主の ID を暗号文に入れ、別の利用者の暗号文は読めない。 */
    private final List<EncryptedSecret> issued = new ArrayList<>();

    private final TotpSecretIssuer issuer = new TotpSecretIssuer() {
        @Override
        public EncryptedSecret issue(UserId owner) {
            EncryptedSecret secret = new EncryptedSecret(owner.value() + "#" + issued.size());
            issued.add(secret);
            return secret;
        }

        @Override
        public TotpProvisioning provisioning(UserId owner, EncryptedSecret secret, String accountName) {
            String text = secret.value();
            if (!text.startsWith(owner.value().toString())) {
                throw new UnreadableCredentialException("別の利用者の暗号文");
            }
            return new TotpProvisioning("otpauth://totp/" + text, "KEY " + text, "<svg/>");
        }
    };

    private final TotpProvisioningStarter starter = new TotpProvisioningStarter(issuer);

    @Test
    void 途中の暗号文がなければ新しく発行する() {
        TotpProvisioningStarter.Started started = starter.start(OWNER, Optional.empty(), ACCOUNT);

        assertThat(issued).singleElement().isEqualTo(started.secret());
        assertThat(started.provisioning().otpauthUri()).endsWith(OWNER.value() + "#0");
    }

    @Test
    void 同じ利用者の途中の暗号文は使い続け新しく発行しない() {
        EncryptedSecret pending = issuer.issue(OWNER);

        TotpProvisioningStarter.Started started = starter.start(OWNER, Optional.of(pending), ACCOUNT);

        assertThat(started.secret()).isEqualTo(pending);
        assertThat(issued).as("発行は最初の 1 回だけ").hasSize(1);
    }

    @Test
    void 読めない途中の暗号文は使い続けず新しく発行する() {
        EncryptedSecret others = issuer.issue(new UserId(UUID.randomUUID()));

        TotpProvisioningStarter.Started started = starter.start(OWNER, Optional.of(others), ACCOUNT);

        assertThat(started.secret()).isNotEqualTo(others);
        assertThat(issued).hasSize(2).last().isEqualTo(started.secret());
    }
}
