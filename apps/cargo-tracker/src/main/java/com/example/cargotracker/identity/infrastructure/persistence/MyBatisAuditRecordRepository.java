package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 監査記録のリポジトリの MyBatis 実装。追記だけを持つ（IA-INV-07）。
 */
@Repository
public class MyBatisAuditRecordRepository implements AuditRecordRepository {

    private final IdentityMapper mapper;

    public MyBatisAuditRecordRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(AuditRecord auditRecord) {
        mapper.insertAuditRecord(new AuditRecordRow(
                auditRecord.id(),
                auditRecord.occurredAt().instant().atOffset(ZoneOffset.UTC),
                auditRecord.actorUserId() == null
                        ? null
                        : auditRecord.actorUserId().value(),
                auditRecord.actorCompanyId() == null
                        ? null
                        : auditRecord.actorCompanyId().value(),
                auditRecord.action().name(),
                auditRecord.result().name(),
                auditRecord.reason()));
    }

    @Override
    public List<AuditRecord> findByActorUserId(UserId actor) {
        return mapper.selectAuditRecordsByActor(actor.value()).stream()
                .map(MyBatisAuditRecordRepository::toAggregate)
                .toList();
    }

    private static AuditRecord toAggregate(AuditRecordRow row) {
        return AuditRecord.reconstitute(
                row.id(),
                new UtcInstant(row.occurredAt().toInstant()),
                optionalUser(row.actorUserId()),
                row.actorCompanyId() == null ? null : new CompanyId(row.actorCompanyId()),
                AuditAction.valueOf(row.action()),
                AuditResult.valueOf(row.result()),
                row.reason());
    }

    private static UserId optionalUser(UUID id) {
        return id == null ? null : new UserId(id);
    }
}
