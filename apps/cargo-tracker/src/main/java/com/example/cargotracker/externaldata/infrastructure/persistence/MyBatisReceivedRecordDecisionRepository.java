package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateReceivedRecordDecisionException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受信記録の判断のリポジトリの MyBatis 実装（Bolt 39d2）。追記専用なので、追加と判断の有無の照会だけを持つ。受信記録 ID の一意制約の違反
 * （{@link DuplicateKeyException}）は、元の例外を原因に持つ {@link DuplicateReceivedRecordDecisionException} に写して上げる（行 6）。追加は
 * セーブポイント（{@code NESTED}）の中で書き、違反したらセーブポイントに戻す（PostgreSQL は制約違反でトランザクションを中断するため。
 * 却下のサービスは判断済みとして同じトランザクションを正常に終える。前例 {@code MyBatisBookingRepository#save}）。
 */
@Repository
public class MyBatisReceivedRecordDecisionRepository implements ReceivedRecordDecisionRepository {

    private final ReceivedRecordDecisionMapper mapper;

    public MyBatisReceivedRecordDecisionRepository(ReceivedRecordDecisionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void add(ReceivedRecordDecision decision) {
        try {
            mapper.insert(new ReceivedRecordDecisionRow(
                    decision.id(),
                    decision.receivedRecordId(),
                    decision.decision().name(),
                    decision.decidedBy().value(),
                    decision.decidedAt().atOffset(ZoneOffset.UTC),
                    decision.reason()));
        } catch (DuplicateKeyException e) {
            throw DuplicateReceivedRecordDecisionException.of(decision.receivedRecordId(), e);
        }
    }

    @Override
    public boolean existsFor(UUID receivedRecordId) {
        return mapper.countByReceivedRecordId(receivedRecordId) > 0;
    }
}
