package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 監査記録のリポジトリの MyBatis 実装。追記だけを持つ（IA-INV-07）。
 */
@Repository
public class MyBatisAuditRecordRepository implements AuditRecordRepository {

    @Override
    public void append(AuditRecord record) {
        // Red の骨組み
    }

    @Override
    public List<AuditRecord> findByActorUserId(UserId actor) {
        return List.of();
    }
}
