package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.application.internal.commandservices.RecoveryCodeVerificationService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpEnrollmentService;
import com.example.cargotracker.identity.application.internal.commandservices.TotpVerificationService;
import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.TotpEnrollmentQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.security.RandomRecoveryCodeGenerator;
import com.example.cargotracker.identity.infrastructure.security.RandomTotpSecretIssuer;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * アクセス・監査コンテキストの組み立て。アプリケーションサービスに送信アダプターをつなぐ。
 */
@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    /** TOTP の秘密の暗号化の鍵の設定の名前（版ごとの Base64 の鍵。環境変数は CARGOTRACKER_TOTPSECRETKEYS_V1）。 */
    static final String TOTP_SECRET_KEYS = "cargotracker.totp-secret-keys";

    static Map<String, String> totpSecretKeys(Environment environment) {
        return Binder.get(environment)
                .bind(TOTP_SECRET_KEYS, Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
    }

    /**
     * TOTP の秘密の暗号化（SEC-16、Bolt 31）。鍵は環境変数で渡し、本番は Secrets Manager から注入する（W10）。鍵がない・
     * 形が違うと起動を失敗させる。
     */
    @Bean
    AesGcmSecretCipher totpSecretCipher(Environment environment) {
        return AesGcmSecretCipher.fromBase64Keys(totpSecretKeys(environment));
    }

    @Bean
    TotpVerificationService totpVerificationService(
            TotpCredentialRepository credentials, TotpCodeGenerator generator, Clock clock) {
        return new TotpVerificationService(credentials, generator, clock);
    }

    @Bean
    RecoveryCodeVerificationService recoveryCodeVerificationService(
            RecoveryCodeRepository codes,
            AuditRecordRepository auditRecords,
            RecoveryCodeHasher hasher,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new RecoveryCodeVerificationService(codes, auditRecords, hasher, clock, transactionTemplate);
    }

    @Bean
    RandomTotpSecretIssuer totpSecretIssuer(AesGcmSecretCipher cipher) {
        return new RandomTotpSecretIssuer(cipher);
    }

    @Bean
    RandomRecoveryCodeGenerator recoveryCodeGenerator() {
        return new RandomRecoveryCodeGenerator();
    }

    @Bean
    @SuppressWarnings("java:S107") // 登録のサービスに要るポートをそろえて渡す
    TotpEnrollmentService totpEnrollmentService(
            TotpCredentialRepository credentials,
            RecoveryCodeRepository recoveryCodes,
            AuditRecordRepository auditRecords,
            TotpSecretIssuer issuer,
            RecoveryCodeGenerator recoveryCodeGenerator,
            RecoveryCodeHasher hasher,
            TotpCodeGenerator generator,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new TotpEnrollmentService(
                credentials,
                recoveryCodes,
                auditRecords,
                issuer,
                recoveryCodeGenerator,
                hasher,
                generator,
                clock,
                transactionTemplate);
    }

    @Bean
    TotpEnrollmentQueryService totpEnrollmentQueryService(TotpCredentialRepository credentials) {
        return new TotpEnrollmentQueryService(credentials);
    }

    @Bean
    KpiObservationEventHandler kpiObservationEventHandler(KpiObservationRepository repository) {
        return new KpiObservationEventHandler(repository);
    }

    @Bean
    KpiObservationQueryService kpiObservationQueryService(KpiObservationRepository repository) {
        return new KpiObservationQueryService(repository);
    }
}
