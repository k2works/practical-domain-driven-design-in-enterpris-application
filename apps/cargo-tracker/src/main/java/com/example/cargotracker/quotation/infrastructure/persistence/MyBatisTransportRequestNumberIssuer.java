package com.example.cargotracker.quotation.infrastructure.persistence;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 業務番号の採番の MyBatis 実装（データモデル「業務番号の採番」、D-10）。
 *
 * <p>呼び出し側（提出）のトランザクションの中で、その年の行を UPDATE で行ロックして 1 増やし、増やした値を読む。
 * 提出が失敗すれば番号も戻るため、欠番が出ない。その年の行がなければ、提出とは別のトランザクションで
 * {@code last_no = 0} の行を作ってから増やす。PostgreSQL ではトランザクションの中で制約に違反すると
 * そのトランザクションが使えなくなるため、提出のトランザクションの中では INSERT しない。
 */
@Repository
public class MyBatisTransportRequestNumberIssuer implements TransportRequestNumberIssuer {

    private final TransportRequestNumberCounterMapper mapper;
    private final TransactionTemplate newTransaction;

    public MyBatisTransportRequestNumberIssuer(
            TransportRequestNumberCounterMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public TransportRequestNumber next(int year) {
        if (mapper.increment(year) == 0) {
            prepareYear(year);
            if (mapper.increment(year) == 0) {
                throw new IllegalStateException("業務番号の採番の行を用意できませんでした: " + year);
            }
        }
        int lastNo = mapper.selectLastNo(year).orElseThrow(() -> new IllegalStateException("業務番号の採番の行がありません: " + year));
        return new TransportRequestNumber(year, lastNo);
    }

    /** その年の行を別のトランザクションで作る。同時に作られて一意制約に違反したら、行は既にあるので何もしない。 */
    private void prepareYear(int year) {
        try {
            newTransaction.executeWithoutResult(status -> mapper.insertYear(year));
        } catch (DuplicateKeyException _) {
            // 別の提出が同じ年の行を先に作った。行はあるので、そのまま増やせばよい
        }
    }
}
