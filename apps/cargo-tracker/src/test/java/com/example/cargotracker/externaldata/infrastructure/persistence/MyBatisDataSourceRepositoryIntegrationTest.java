package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepositoryContract;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 情報源のリポジトリの MyBatis 実装を PostgreSQL 18 で確かめる（Bolt 39b の行 6）。契約を継ぐ。テストのトランザクションは契約のクラスの
 * {@code @Transactional} で張られ、入れた行はテストの終わりにロールバックで消える（情報源の表は追記専用でない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisDataSourceRepositoryIntegrationTest extends DataSourceRepositoryContract {

    @Autowired
    DataSourceRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SqlSessionTemplate sqlSession;

    @Override
    protected DataSourceRepository repository() {
        return repository;
    }

    /**
     * JDBC で行を書く。同じトランザクションの MyBatis のセッションは前の {@code countApproved} の結果をキャッシュに持ち、JDBC の書き込みでは
     * 消えないので、書いた後にセッションのキャッシュを消す。
     */
    @Override
    protected void given(DataSource source) {
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, ?, 0)",
                source.id(),
                source.provider(),
                source.recordType(),
                source.approvalStatus().name());
        sqlSession.clearCache();
    }
}
