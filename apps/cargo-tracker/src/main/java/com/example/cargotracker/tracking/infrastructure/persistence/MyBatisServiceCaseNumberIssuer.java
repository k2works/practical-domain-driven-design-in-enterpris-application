package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 受付番号の採番の MyBatis 実装（経路設計の {@code MyBatisRoutingCaseNumberIssuer} と同じ方式。Bolt 40-6）。
 *
 * <p>呼び手（起票）のトランザクションの中で、その年の行を UPDATE で行ロックして 1 増やし、増やした値を読む。起票が失敗すれば番号も戻るため、欠番が出ない。
 * その年の行がなければ、別のトランザクションで {@code last_no = 0} の行を作ってから増やす（PostgreSQL ではトランザクションの中の制約の違反で
 * トランザクションが使えなくなるため）。分離レベルは READ COMMITTED（PostgreSQL と H2 の既定）を前提にする。
 */
@Repository
public class MyBatisServiceCaseNumberIssuer implements ServiceCaseNumberIssuer {

    private final ServiceCaseNumberCounterMapper mapper;
    private final TransactionTemplate newTransaction;

    public MyBatisServiceCaseNumberIssuer(
            ServiceCaseNumberCounterMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CaseNumber next(int year) {
        if (mapper.increment(year) == 0) {
            prepareYear(year);
            if (mapper.increment(year) == 0) {
                throw new IllegalStateException("受付番号の採番の行を用意できませんでした: " + year);
            }
        }
        int lastNo = mapper.selectLastNo(year).orElseThrow(() -> new IllegalStateException("受付番号の採番の行がありません: " + year));
        return CaseNumber.of(year, lastNo);
    }

    /** その年の行がなければ、別のトランザクションで作る。同時に作られて一意制約に違反したら、行は既にあるので何もしない。 */
    private void prepareYear(int year) {
        try {
            newTransaction.executeWithoutResult(status -> mapper.insertYear(year));
        } catch (DuplicateKeyException _) {
            // 別の起票が同じ年の行を先に作った。行はあるので、そのまま増やせばよい
        }
    }
}
