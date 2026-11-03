package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalDataRecordRepositoriesContract;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 取込・外部原本・受信記録のリポジトリの契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 39b。マッパーの SQL が H2 と
 * PostgreSQL の共通の構文で動くこと。ADR-007）。H2 は権限で追記専用を守れないので、ポートに更新・削除のメソッドを持たせない。
 * テストのトランザクションは契約のクラスの {@code @Transactional} で開き、書いた行はロールバックで消える。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2ExternalDataRecordRepositoriesTest extends ExternalDataRecordRepositoriesContract {

    @Autowired
    IngestionRepository ingestions;

    @Autowired
    ExternalRecordRepository externalRecords;

    @Autowired
    ReceivedRecordRepository receivedRecords;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected IngestionRepository ingestions() {
        return ingestions;
    }

    @Override
    protected ExternalRecordRepository externalRecords() {
        return externalRecords;
    }

    @Override
    protected ReceivedRecordRepository receivedRecords() {
        return receivedRecords;
    }

    @Override
    protected void given(DataSource source) {
        MyBatisExternalDataRecordRepositoriesIntegrationTest.insertDataSource(jdbc, source);
    }

    @Override
    protected Optional<Ingestion> storedIngestion(UUID id) {
        return MyBatisExternalDataRecordRepositoriesIntegrationTest.readIngestion(jdbc, id);
    }

    @Override
    protected Optional<ReceivedRecord> storedReceivedRecord(UUID id) {
        return MyBatisExternalDataRecordRepositoriesIntegrationTest.readReceivedRecord(jdbc, id);
    }
}
