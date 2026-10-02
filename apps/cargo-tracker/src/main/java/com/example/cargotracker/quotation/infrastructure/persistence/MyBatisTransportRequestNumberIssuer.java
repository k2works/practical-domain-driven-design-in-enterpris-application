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
 *
 * <p>別のトランザクションは、外側の接続を握ったまま 2 本目の接続を取る。年の最初の提出が接続のプールの大きさ以上に
 * 同時に来ると枯渇しうるため、年の行は起動時に今年と来年の分を {@link #prepareYear(int)} で用意しておく
 * （D-13、Bolt 4 レビュー R-03）。提出の中の別のトランザクションは、年をまたいで動き続けた場合の予備である。
 *
 * <p>年の行を作った後の 2 回目の UPDATE がその行を見つけられるのは、トランザクションの分離レベルが
 * READ COMMITTED（PostgreSQL と H2 の既定）だからである。REPEATABLE READ 以上に上げると見つけられず失敗する。
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

    /**
     * その年の行がなければ、別のトランザクションで作る。同時に作られて一意制約に違反したら、行は既にあるので何もしない。
     * 起動時に今年と来年の分を用意するために公開する（D-13）。
     */
    public void prepareYear(int year) {
        try {
            newTransaction.executeWithoutResult(status -> mapper.insertYear(year));
        } catch (DuplicateKeyException _) {
            // 別の提出が同じ年の行を先に作った。行はあるので、そのまま増やせばよい
        }
    }
}
