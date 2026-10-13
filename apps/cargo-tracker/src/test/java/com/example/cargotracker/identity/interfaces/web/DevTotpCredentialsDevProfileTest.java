package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.infrastructure.devlogin.DevLoginProperties;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.security.Base32;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 開発用の利用者の TOTP の資格（Bolt 31、ADR-011 の dev の 4 層目）。同じ秘密を dev-data の暗号文と設定の Base32 の 2 か所に
 * 置くので、ずれると dev のログインが黙って失敗する。dev の鍵で復号した暗号文が設定の秘密と一致し、利用者ごとに秘密が違うことを
 * 確かめる。
 */
@SpringBootTest
@ActiveProfiles("dev")
class DevTotpCredentialsDevProfileTest {

    @Autowired
    DevLoginProperties devLogin;

    @Autowired
    UserRepository users;

    @Autowired
    TotpCredentialRepository credentials;

    @Autowired
    AesGcmSecretCipher cipher;

    @Test
    void 開発用の利用者はみなTOTPを登録し暗号文を復号すると設定の秘密と一致し利用者ごとに違う() {
        Set<String> secrets = new HashSet<>();
        assertThat(devLogin.accounts()).hasSize(5);
        for (DevLoginProperties.Account account : devLogin.accounts()) {
            TotpCredential credential = credentials
                    .findByUserId(users.findByEmail(EmailAddress.of(account.email()))
                            .orElseThrow()
                            .id())
                    .orElseThrow();

            assertThat(cipher.decrypt(credential.secret(), credential.userId()))
                    .as(account.email())
                    .isEqualTo(Base32.decode(account.totpSecret()));
            secrets.add(account.totpSecret());
        }
        assertThat(secrets).as("利用者ごとに別の秘密").hasSize(devLogin.accounts().size());
    }
}
