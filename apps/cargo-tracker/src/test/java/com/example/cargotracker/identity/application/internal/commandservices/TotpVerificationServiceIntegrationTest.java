package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.OverlappingTransactions;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 同じコードの同時の確かめのうち 1 つだけが通ることを、本物の暗号化・生成器・PostgreSQL で確かめる（Bolt 31、IA-INV-10）。
 * 先の確かめのコミットを止め、後の確かめがその行ロックを待っていることを確かめてから放す（T-100）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TotpVerificationServiceIntegrationTest {

    @Autowired
    TotpVerificationService service;

    @Autowired
    TotpCredentialRepository credentials;

    @Autowired
    TotpCodeGenerator generator;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    Clock clock;

    @Test
    void 重なった同じコードの確かめは先の1つだけが通り後は再利用として拒む() {
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        TotpCredential credential = TotpCredential.enroll(
                user,
                cipher.encrypt("12345678901234567890".getBytes(StandardCharsets.US_ASCII)),
                new UtcInstant(clock.instant()));
        credentials.add(credential);
        String code = generator.code(credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));

        List<TotpVerification> results = OverlappingTransactions.run(
                transactionTemplate, () -> service.verify(user, code), () -> service.verify(user, code));

        assertThat(results).containsExactly(TotpVerification.ACCEPTED, TotpVerification.REJECTED_REUSED);
    }
}
