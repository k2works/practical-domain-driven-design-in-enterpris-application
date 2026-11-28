package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部原本の保存の MyBatis 実装（Bolt 39a2、ADR-004 の改訂）。W7〜W9 は原本を DB の {@code external_data.stored_payload} に
 * 置き、W10 で S3 の実装に差し替える。
 *
 * <ul>
 *   <li>保存は外側のトランザクションから独立したトランザクション（REQUIRES_NEW）で書き、外側が巻き戻っても原本を残す
 *   <li>主キーの違反は独立したトランザクションの外で捕まえて {@link IllegalStateException} にする。外側のトランザクションを
 *       巻き戻し専用にしない
 *   <li>読み書きで配列を写す。呼び手の書き換えから原本を守り、MyBatis の 1 次キャッシュが同じ SqlSession の中の 2 回の読み出しで
 *       同じ配列を返しても、呼び手どうしで配列を共有しない
 *   <li>読み出しは独立したトランザクションを開かず、外側のトランザクションがあればその接続で読む。保存は REQUIRES_NEW で読む前に
 *       コミットされ、外側が READ COMMITTED（PostgreSQL の既定）なので、外側の中で保存した原本をすぐに読める
 *   <li>保存した時刻は Clock から取る（Clock の Bean はマイクロ秒に切り捨てる。BR-10、ADR-007）
 * </ul>
 */
@Repository
public class MyBatisExternalPayloadStorage implements ExternalPayloadStorage {

    private final StoredPayloadMapper mapper;
    private final TransactionTemplate independentTransaction;
    private final Clock clock;

    public MyBatisExternalPayloadStorage(
            StoredPayloadMapper mapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.mapper = mapper;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public void store(PayloadObjectKey key, byte[] payload) {
        Objects.requireNonNull(key, "key");
        byte[] content = Objects.requireNonNull(payload, "payload").clone();
        try {
            independentTransaction.executeWithoutResult(
                    status -> mapper.insert(key.value(), content, OffsetDateTime.now(clock)));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("原本は上書きしない: " + key.value(), e);
        }
    }

    @Override
    public byte[] read(PayloadObjectKey key) {
        Objects.requireNonNull(key, "key");
        return mapper.selectContent(key.value())
                .map(byte[]::clone)
                .orElseThrow(() -> new NoSuchElementException("原本がない: " + key.value()));
    }
}
