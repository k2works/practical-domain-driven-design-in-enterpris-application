package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorageContract;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
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

    private static final String INSERT_OTHER_ROW =
            "INSERT INTO external_data.stored_payload (object_key, content, stored_at)"
                    + " VALUES (?, ?, CURRENT_TIMESTAMP)";

    @Autowired
    ExternalPayloadStorage storage;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

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

    private Integer rowsOf(PayloadObjectKey key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key = ?", Integer.class, key.value());
    }

    @Test
    void 外側のトランザクションが巻き戻っても保存した原本は残る() {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        PayloadObjectKey outerRow = PayloadObjectKey.newRecordPayload();

        outer().executeWithoutResult(status -> {
            storage.store(key, bytes("V-1,1"));
            jdbc.update(INSERT_OTHER_ROW, outerRow.value(), bytes("outer"));
            status.setRollbackOnly();
        });

        assertThat(storage.read(key)).isEqualTo(bytes("V-1,1"));
        assertThat(rowsOf(outerRow)).as("外側の書き込みは巻き戻った（対照）").isZero();
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
        byte[] second = bytes("second");

        outer().executeWithoutResult(status -> {
            storage.store(key, bytes("first"));
            assertThatThrownBy(() -> storage.store(key, second)).isInstanceOf(IllegalStateException.class);
            jdbc.update(INSERT_OTHER_ROW, afterRefusal.value(), bytes("outer"));
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

    @Test
    void 大きなpayloadも往復で一致する() {
        PayloadObjectKey key = PayloadObjectKey.newIngestionFile();
        byte[] large = new byte[5 * 1024 * 1024];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i * 31);
        }

        storage.store(key, large);

        assertThat(storage.read(key)).isEqualTo(large);
    }

    @Test
    void 同じキーへの同時の保存は片方だけが保存され接続を残さない() throws Exception {
        PayloadObjectKey key = PayloadObjectKey.newRecordPayload();
        byte[] first = bytes("first");
        byte[] second = bytes("second");
        int activeBefore = activeConnections();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<byte[]>> results = List.of(
                    executor.submit(storeAfter(start, key, first)), executor.submit(storeAfter(start, key, second)));
            start.countDown();

            List<byte[]> stored = results.stream()
                    .map(this::resultOf)
                    .filter(Objects::nonNull)
                    .toList();

            assertThat(stored).as("片方だけが保存された（もう片方は IllegalStateException）").hasSize(1);
            assertThat(storage.read(key)).isEqualTo(stored.get(0));
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeConnections()).as("使った接続をプールに返した").isEqualTo(activeBefore);
    }

    /** 合図を待って保存し、保存したバイト列を返す。上書きを拒まれたら null を返す。 */
    private Callable<byte[]> storeAfter(CountDownLatch start, PayloadObjectKey key, byte[] payload) {
        return () -> {
            start.await();
            try {
                storage.store(key, payload);
                return payload;
            } catch (IllegalStateException refused) {
                return null;
            }
        };
    }

    private byte[] resultOf(Future<byte[]> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("保存の結果を待つ間に割り込まれた", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("保存が上書きの拒否のほかの理由で失敗した", e);
        }
    }

    private int activeConnections() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }
}
