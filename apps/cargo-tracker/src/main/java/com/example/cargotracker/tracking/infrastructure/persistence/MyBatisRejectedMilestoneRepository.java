package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 拒否した主要実績のリポジトリの MyBatis 実装（Bolt 39g）。同じ出典の記録が同時に書かれて一意制約に違反したら、セーブポイントに戻して既存とみなす
 * （PostgreSQL は制約違反でトランザクションを中断するため。呼び手のトランザクションを続ける）。
 */
@Repository
public class MyBatisRejectedMilestoneRepository implements RejectedMilestoneRepository {

    private final RejectedMilestoneMapper mapper;
    private final TransactionTemplate savepoint;

    public MyBatisRejectedMilestoneRepository(
            RejectedMilestoneMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.savepoint = new TransactionTemplate(transactionManager);
        this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    @Override
    public boolean existsBySource(Source source) {
        return mapper.countBySource(source.kind().name(), source.reference()) > 0;
    }

    @Override
    public void saveIfAbsent(RejectedMilestone rejected) {
        RejectedMilestoneRow row = toRow(rejected);
        try {
            savepoint.executeWithoutResult(status -> mapper.insert(row));
        } catch (DuplicateKeyException _) {
            // 同時の配信の負けた側。勝った側の記録があるので既存とみなす
        }
    }

    private static RejectedMilestoneRow toRow(RejectedMilestone rejected) {
        return new RejectedMilestoneRow(
                rejected.id(),
                rejected.trackingNumber().value(),
                rejected.kind().name(),
                rejected.location().unLocode(),
                offset(rejected.occurredAt()),
                rejected.source().kind().name(),
                rejected.source().reference(),
                offset(rejected.source().acquiredAt()),
                rejected.dataSourceId(),
                rejected.externalId(),
                rejected.reason().name(),
                offset(rejected.recordedAt()));
    }

    private static OffsetDateTime offset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}
