package com.example.cargotracker;

import com.example.cargotracker.integration.ConnectionUsageRecorder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 18 の統合テストの組み立て。接続とコミットを数える部品（{@link ConnectionUsageRecorder}。Bolt 39b4）もここで読み込み、
 * 部品を使うテストが Spring の文脈を増やさないようにする（記録していないときは接続を包まない）。
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(ConnectionUsageRecorder.class)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
                // アプリケーション利用者を作り、Flyway の afterMigrate が権限を与えられるようにする（DA-02）
                .withInitScript("db/testcontainers/init-app-user.sql");
    }
}
