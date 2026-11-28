package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * イベント経由の監査記録の照会の契約を PostgreSQL 18 で確かめる（Bolt 39b3b、ADR-007）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisAuditRecordEventAuditIntegrationTest extends AuditRecordEventAuditContract {

    @Autowired
    AuditRecordRepository auditRecords;

    @Override
    protected AuditRecordRepository auditRecords() {
        return auditRecords;
    }
}
