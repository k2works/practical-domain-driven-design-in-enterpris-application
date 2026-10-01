package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepositoryContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * スモーク（H2）: ローカル起動と同じ dev プロファイルで、アプリケーションが起動しマイグレーションが通る（ADR-007、テスト戦略）。
 * H2 でも動く必要のある SQL（KPI 計測記録の冪等な保存）は、リポジトリの契約で確かめる。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class H2DevProfileSmokeTest extends KpiObservationRepositoryContract {

    @Autowired
    Flyway flyway;

    @Autowired
    KpiObservationRepository repository;

    @Override
    protected KpiObservationRepository repository() {
        return repository;
    }

    @Test
    void H2で起動しマイグレーションがすべて適用される() {
        assertThat(flyway.getConfiguration().getDataSource()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).isNotEmpty();
    }
}
