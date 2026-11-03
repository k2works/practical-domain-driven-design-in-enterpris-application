package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepositoryContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情報源のリポジトリの MyBatis 実装を PostgreSQL 18 で確かめる（Bolt 39b の行 6）。契約を継ぐ。情報源の表は追記専用でないので、
 * テストのトランザクションで入れた行はロールバックで消える。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisDataSourceRepositoryIntegrationTest extends DataSourceRepositoryContract {

    @Autowired
    DataSourceRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected DataSourceRepository repository() {
        return repository;
    }

    @Override
    protected void given(DataSource source) {
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, ?, 0)",
                source.id(),
                source.provider(),
                source.recordType(),
                source.approvalStatus().name());
    }
}
