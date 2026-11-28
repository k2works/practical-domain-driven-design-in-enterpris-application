package com.example.cargotracker.ui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * アプリをアプリケーション利用者（最小の権限）で動かす（運用要件 DA-02、D-9 の提案）。
 * Flyway は Testcontainers の所有者（FlywayConnectionDetails）のまま動かし、アプリのデータソースだけを差し替える。
 * 本番と同じく、アプリが追記専用の表を更新しようとすると権限なしで失敗する。
 */
@TestConfiguration(proxyBeanMethods = false)
public class ApplicationUserConnection {

    /** テスト用のパスワード（db/testcontainers/init-app-user.sql と同じ値）。 */
    private static final String APP_PASSWORD = "cargo_tracker_app_test";

    @Bean
    @Primary
    JdbcConnectionDetails applicationUserConnectionDetails(
            PostgreSQLContainer postgres, @Value("${spring.flyway.placeholders.appuser}") String appUser) {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return appUser;
            }

            @Override
            public String getPassword() {
                return APP_PASSWORD;
            }

            @Override
            public String getJdbcUrl() {
                return postgres.getJdbcUrl();
            }
        };
    }
}
