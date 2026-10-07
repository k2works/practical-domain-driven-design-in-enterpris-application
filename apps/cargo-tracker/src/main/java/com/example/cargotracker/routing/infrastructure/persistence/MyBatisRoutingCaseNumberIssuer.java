package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 案件番号の採番の MyBatis 実装（業務番号の採番 {@code MyBatisTransportRequestNumberIssuer} と同じ方式。D-10、D-13。Bolt 17）。
 *
 * <p>呼び出し側（案件の作成）のトランザクションの中で、その年の行を UPDATE で行ロックして 1 増やし、増やした値を読む。
 * 作成が失敗すれば番号も戻るため、欠番が出ない。その年の行がなければ、別のトランザクションで {@code last_no = 0} の行を作ってから
 * 増やす（PostgreSQL ではトランザクションの中の制約の違反でトランザクションが使えなくなるため）。年の行は起動時に今年と来年の分を
 * {@link #prepareYear(int)} で用意しておく。分離レベルは READ COMMITTED（PostgreSQL と H2 の既定）を前提にする。
 */
@Repository
public class MyBatisRoutingCaseNumberIssuer implements RoutingCaseNumberIssuer {

    private final RoutingCaseNumberCounterMapper mapper;
    private final TransactionTemplate newTransaction;

    public MyBatisRoutingCaseNumberIssuer(
            RoutingCaseNumberCounterMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public RoutingCaseNumber next(int year) {
        if (mapper.increment(year) == 0) {
            prepareYear(year);
            if (mapper.increment(year) == 0) {
                throw new IllegalStateException("案件番号の採番の行を用意できませんでした: " + year);
            }
        }
        int lastNo = mapper.selectLastNo(year).orElseThrow(() -> new IllegalStateException("案件番号の採番の行がありません: " + year));
        return new RoutingCaseNumber(year, lastNo);
    }

    /** その年の行がなければ、別のトランザクションで作る。同時に作られて一意制約に違反したら、行は既にあるので何もしない。 */
    public void prepareYear(int year) {
        try {
            newTransaction.executeWithoutResult(status -> mapper.insertYear(year));
        } catch (DuplicateKeyException _) {
            // 別の作成が同じ年の行を先に作った。行はあるので、そのまま増やせばよい
        }
    }
}
