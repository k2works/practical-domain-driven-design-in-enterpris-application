package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.infrastructure.devlogin.DevLoginGuard;
import com.example.cargotracker.identity.infrastructure.devlogin.DevLoginProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 開発環境だけの設定の守り（{@link DevLoginGuard}）を、Flyway の設定を組み立てるときに動かす。マイグレーションより前に
 * 起動を止め、開発用の利用者が dev の外の DB に入らないようにする（Bolt 14 レビュー）。
 */
@Configuration(proxyBeanMethods = false)
public class DevLoginGuardConfiguration {

    @Bean
    FlywayConfigurationCustomizer devLoginGuard(DevLoginProperties properties, Environment environment) {
        return configuration -> DevLoginGuard.check(properties, environment);
    }
}
