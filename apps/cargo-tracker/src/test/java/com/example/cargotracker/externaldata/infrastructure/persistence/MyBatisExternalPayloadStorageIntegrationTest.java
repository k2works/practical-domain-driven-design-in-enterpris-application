package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorageContract;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部原本の保存の DB の実装を PostgreSQL 18 で確かめる（Bolt 39a2 の行 1〜5、ADR-004 の改訂）。契約を継ぎ、外側のトランザクションとの
 * 関係（独立したトランザクションで保存し、外側が巻き戻っても原本を残す）を足す。
 *
 * <p>クラスに {@code @Transactional} を付けない。付けると外側の {@link TransactionTemplate} がテストのトランザクションに合流し、
 * 巻き戻りを確かめられない。キーは乱数の UUID なので、コミットされて残った行どうしは干渉しない。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisExternalPayloadStorageIntegrationTest extends ExternalPayloadStorageContract {

    @Autowired
    ExternalPayloadStorage storage;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected ExternalPayloadStorage storage() {
        return storage;
    }

    private TransactionTemplate outer() {
        return new TransactionTemplate(transactionManager);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private int rowsOf(PayloadObjectKey key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key = ?", Integer.class, key.value());
        return count == null ? 0 : count;
    }

    @Test
    void 外側のトランザクションが巻き戻っても保存した原本は残る() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();

        outer().executeWithoutResult(status -> {
            storage.store(key, bytes("V-1,1"));
            status.setRollbackOnly();
        });

        assertThat(storage.read(key)).isEqualTo(bytes("V-1,1"));
    }

    @Test
    void 外側のトランザクションがコミットすると原本は1行だけ残る() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();

        outer().executeWithoutResult(status -> storage.store(key, bytes("V-1,1")));

        assertThat(storage.read(key)).isEqualTo(bytes("V-1,1"));
        assertThat(rowsOf(key)).isEqualTo(1);
    }

    @Test
    void 外側のトランザクションの中で上書きを拒まれても外側はコミットでき最初のバイト列が残る() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        PayloadObjectKey afterRefusal = PayloadObjectKey.newRecordPayload();

        outer().executeWithoutResult(status -> {
            storage.store(key, bytes("first"));
            assertThatThrownBy(() -> storage.store(key, bytes("second"))).isInstanceOf(IllegalStateException.class);
            jdbc.update(
                    "INSERT INTO external_data.stored_payload (object_key, content, stored_at)"
                            + " VALUES (?, ?, CURRENT_TIMESTAMP)",
                    afterRefusal.value(),
                    bytes("outer"));
        });

        assertThat(storage.read(key)).isEqualTo(bytes("first"));
        assertThat(storage.read(afterRefusal)).as("外側の書き込みも巻き戻し専用にならずにコミットされた").isEqualTo(bytes("outer"));
    }

    @Test
    void 外側のトランザクションの中で読んだ配列を書き換えても読み直すと元のまま() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        storage.store(key, bytes("V-1,1"));

        byte[] reread = outer().execute(status -> {
            storage.read(key)[0] = 'X';
            return storage.read(key);
        });

        assertThat(reread).isEqualTo(bytes("V-1,1"));
    }
}
