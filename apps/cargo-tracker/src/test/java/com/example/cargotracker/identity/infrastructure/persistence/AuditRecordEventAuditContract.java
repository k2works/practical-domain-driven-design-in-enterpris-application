package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * イベント経由の監査記録の照会の契約（Bolt 39b3b。外部原本の採否の監査、IA-INV-15）。操作と対象で引いて、再配信で同じ記録を重ねないための
 * 照会と、外部原本の採否の記録が書けることを、H2（dev）と PostgreSQL の両方で確かめる（ADR-007、T-214）。実装は MyBatis だけなので、
 * この契約は persistence に置く（前例 {@code AuditRecordSuspensionContract}）。{@code @Transactional} は契約のクラスに付ける（T-231）。
 */
@Transactional
abstract class AuditRecordEventAuditContract {

    private static final String RECEIVED_RECORD = "RECEIVED_RECORD";

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));

    protected abstract AuditRecordRepository auditRecords();

    private static UserId newUser() {
        return new UserId(UUID.randomUUID());
    }

    private static CompanyId newCompany() {
        return new CompanyId(UUID.randomUUID());
    }

    @Test
    void 行3_外部版が100文字の隔離の要約を書けて版は持たない() {
        UserId actor = newUser();
        String receivedRecordId = UUID.randomUUID().toString();
        String summary = "provider=P;recordType=VOYAGE;externalId=V001;externalVersion=" + "9".repeat(100);

        auditRecords()
                .append(AuditRecord.externalRecordQuarantined(
                        actor, newCompany(), receivedRecordId, "CONTENT_MISMATCH", summary, AT));

        assertThat(auditRecords().findByActorUserId(actor)).singleElement().satisfies(written -> {
            assertThat(written.action()).isEqualTo(AuditAction.EXTERNAL_RECORD_QUARANTINED);
            assertThat(written.reason()).isEqualTo("CONTENT_MISMATCH");
            assertThat(written.target())
                    .isEqualTo(new AuditTarget(RECEIVED_RECORD, receivedRecordId, null, null, summary));
        });
    }

    @Test
    void 行3_各部品を上限にそろえた隔離の要約が変更後の列に入る() {
        // 提供元 100・外部 ID 200・外部版 100（V20261103100000 の列の上限）と最大の物理行。要約は監査の listener で作る
        UserId actor = newUser();
        UUID receivedRecordId = UUID.randomUUID();
        ExternalRecordQuarantined event = new ExternalRecordQuarantined(
                receivedRecordId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "P".repeat(100),
                "MILESTONE",
                "E".repeat(200),
                "9".repeat(100),
                ExternalRecordQuarantined.CONTENT_MISMATCH,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                actor,
                newCompany(),
                AT);

        new ExternalRecordAuditEventHandler(auditRecords()).on(event);

        assertThat(auditRecords().findByActorUserId(actor)).singleElement().satisfies(written -> {
            assertThat(written.target().id()).isEqualTo(receivedRecordId.toString());
            assertThat(written.target().afterState())
                    .contains("P".repeat(100), "E".repeat(200), "9".repeat(100))
                    .hasSizeLessThanOrEqualTo(4000);
        });
    }

    @Test
    void 行5_操作と対象で引くと追記した記録があるかが分かる() {
        String receivedRecordId = UUID.randomUUID().toString();
        assertThat(auditRecords()
                        .existsByActionAndTarget(
                                AuditAction.EXTERNAL_RECORD_ADOPTED, RECEIVED_RECORD, receivedRecordId))
                .isFalse();

        auditRecords()
                .append(AuditRecord.externalRecordAdopted(
                        newUser(), newCompany(), receivedRecordId, "recordType=VOYAGE", AT));

        assertThat(auditRecords()
                        .existsByActionAndTarget(
                                AuditAction.EXTERNAL_RECORD_ADOPTED, RECEIVED_RECORD, receivedRecordId))
                .isTrue();
    }

    @Test
    void 行5_ほかの操作とほかの対象の記録では見つからない() {
        String receivedRecordId = UUID.randomUUID().toString();
        auditRecords()
                .append(AuditRecord.externalRecordAdopted(
                        newUser(), newCompany(), receivedRecordId, "recordType=VOYAGE", AT));

        assertThat(auditRecords()
                        .existsByActionAndTarget(
                                AuditAction.EXTERNAL_RECORD_QUARANTINED, RECEIVED_RECORD, receivedRecordId))
                .isFalse();
        assertThat(auditRecords()
                        .existsByActionAndTarget(
                                AuditAction.EXTERNAL_RECORD_ADOPTED,
                                RECEIVED_RECORD,
                                UUID.randomUUID().toString()))
                .isFalse();
        assertThat(auditRecords()
                        .existsByActionAndTarget(AuditAction.EXTERNAL_RECORD_ADOPTED, "USER", receivedRecordId))
                .isFalse();
    }
}
