package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
    KpiObservationEventHandler kpiObservationEventHandler(KpiObservationRepository repository) {
        return new KpiObservationEventHandler(repository);
    }

    @Bean
    KpiObservationQueryService kpiObservationQueryService(KpiObservationRepository repository) {
        return new KpiObservationQueryService(repository);
    }
}
