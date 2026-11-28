package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepositoryContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 採用値のリポジトリの契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 39b3 の行 1・15。マッパーの SQL が H2 と
 * PostgreSQL の共通の構文で動くこと。ADR-007。前例は {@link H2ExternalDataRecordRepositoriesTest}）。採用値の外部キーの先は
 * PostgreSQL のテストと同じ書き方で用意する。テストのトランザクションは契約のクラスの {@code @Transactional} で開き（T-231）、書いた行は
 * ロールバックで消える。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2AdoptedValueRepositoryTest extends AdoptedValueRepositoryContract {

    @Autowired
    AdoptedValueRepository adoptedValues;

    @Autowired
    IngestionRepository ingestions;

    @Autowired
    ExternalRecordRepository externalRecords;

    @Autowired
    ReceivedRecordRepository receivedRecords;

    @Autowired
    JdbcTemplate jdbc;

    /** この外部版の採用値の契約の外部原本の提供元（テストのインスタンスごと。Bolt 39d3）。 */
    private final String provider = "採用値の提供元-" + UUID.randomUUID();

    @Override
    protected AdoptedValueRepository adoptedValues() {
        return adoptedValues;
    }

    @Override
    protected UUID givenReceivedRecord() {
        return MyBatisAdoptedValueRepositoryIntegrationTest.insertAdoptedReceivedRecord(
                jdbc, ingestions, externalRecords, receivedRecords);
    }

    @Override
    protected UUID givenExternalRecord(String externalId, String externalVersion) {
        return MyBatisAdoptedValueRepositoryIntegrationTest.insertExternalRecord(
                externalRecords, provider, externalId, externalVersion);
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId) {
        return MyBatisAdoptedValueRepositoryIntegrationTest.insertAdoptedReceivedRecordOf(
                jdbc, ingestions, receivedRecords, externalRecordId);
    }

    @Override
    protected UUID givenExternalRecordOf(
            String provider, String recordType, String externalId, String externalVersion) {
        return MyBatisAdoptedValueRepositoryIntegrationTest.insertExternalRecord(
                externalRecords, provider, recordType, externalId, externalVersion);
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId, Instant acquiredAt, Classification classification) {
        return MyBatisAdoptedValueRepositoryIntegrationTest.insertReceivedRecordOf(
                jdbc, ingestions, receivedRecords, externalRecordId, acquiredAt, classification);
    }
}
