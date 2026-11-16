package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

/**
 * 外部原本の採否の監査記録の二重の防ぎ（W10 の Bolt 53-1、H-4。Bolt 39b3b のステップ 7 の決定）を PostgreSQL で確かめる。
 * 「操作と対象で引いて記録があれば書かない」（ハンドラー）は、照会と追記の間に別のインスタンスが書くと二重になるので、
 * 同じ操作・対象の記録は一意制約（部分索引。PostgreSQL のベンダーの移行）で 1 つに限る。ログインの失敗など、同じ対象を持たない
 * 記録は何度でも書ける。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisAuditRecordUniqueIntegrationTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-16T00:00:00Z"));

    @Autowired
    AuditRecordRepository auditRecords;

    private static AuditRecord adopted(String receivedRecordId) {
        return AuditRecord.externalRecordAdopted(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                receivedRecordId,
                "recordType=VOYAGE",
                AT);
    }

    private static AuditRecord quarantined(String receivedRecordId) {
        return AuditRecord.externalRecordQuarantined(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                receivedRecordId,
                "CONTENT_MISMATCH",
                "provider=P;recordType=VOYAGE",
                AT);
    }

    @Test
    void 同じ操作と受信記録の外部原本の監査記録は2つ書けない() {
        String receivedRecordId = UUID.randomUUID().toString();
        auditRecords.append(adopted(receivedRecordId));

        assertThatThrownBy(() -> auditRecords.append(adopted(receivedRecordId)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 同じ受信記録でも_操作が違えば書ける() {
        String receivedRecordId = UUID.randomUUID().toString();

        auditRecords.append(quarantined(receivedRecordId));
        auditRecords.append(adopted(receivedRecordId));

        assertThat(auditRecords.existsByActionAndTarget(
                        AuditAction.EXTERNAL_RECORD_QUARANTINED, "RECEIVED_RECORD", receivedRecordId))
                .isTrue();
        assertThat(auditRecords.existsByActionAndTarget(
                        AuditAction.EXTERNAL_RECORD_ADOPTED, "RECEIVED_RECORD", receivedRecordId))
                .isTrue();
    }
}
