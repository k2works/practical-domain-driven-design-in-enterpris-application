package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * イベント経由の監査記録の照会の契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 39b3b、ADR-007）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2AuditRecordEventAuditTest extends AuditRecordEventAuditContract {

    @Autowired
    AuditRecordRepository auditRecords;

    @Override
    protected AuditRecordRepository auditRecords() {
        return auditRecords;
    }
}
