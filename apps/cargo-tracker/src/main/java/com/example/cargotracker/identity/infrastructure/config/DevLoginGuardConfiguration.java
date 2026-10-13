package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.infrastructure.devlogin.DevLoginGuard;
import com.example.cargotracker.identity.infrastructure.devlogin.DevLoginProperties;
import com.example.cargotracker.identity.infrastructure.devlogin.DevTotpCodes;
import com.example.cargotracker.identity.infrastructure.security.AesGcmSecretCipher;
import com.example.cargotracker.identity.interfaces.web.DevTotpCodeSource;
import java.time.Clock;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * 開発環境だけの設定の守り（{@link DevLoginGuard}）を、Flyway の設定を組み立てるときに動かす。マイグレーションより前に
 * 起動を止め、開発用の利用者が dev の外の DB に入らないようにする（Bolt 14 レビュー）。
 */
@Configuration(proxyBeanMethods = false)
public class DevLoginGuardConfiguration {

    /**
     * dev の A-02 の入力済みのコード（Bolt 31）。dev プロファイルでだけ組み立てる（守りの 1 層目）。画面の層の関数の型につなぐのは
     * 合成ルートだけに許した例外（LayerArchitectureTest、ADR-011。2026-10-13 の人の決定）。
     */
    @Bean
    @Profile("dev")
    DevTotpCodeSource devTotpCodes(
            DevLoginProperties properties, AesGcmSecretCipher cipher, TotpCodeGenerator generator, Clock clock) {
        return new DevTotpCodes(properties, cipher, generator, clock)::codeFor;
    }

    @Bean
    FlywayConfigurationCustomizer devLoginGuard(DevLoginProperties properties, Environment environment) {
        return configuration -> DevLoginGuard.check(properties, environment);
    }
}
