package com.example.cargotracker.externaldata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 外部原本の保存の DB の実装のうち、DB を使わずに確かめられる振る舞い（Bolt 39a2 のレビュー）。 */
class MyBatisExternalPayloadStorageTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:04:05.123456Z");

    /** 渡された保存した時刻を捕まえるマッパー。 */
    private static final class RecordingMapper implements StoredPayloadMapper {

        private final List<OffsetDateTime> storedAts = new ArrayList<>();

        @Override
        public void insert(String objectKey, byte[] content, OffsetDateTime storedAt) {
            storedAts.add(storedAt);
        }

        @Override
        public Optional<byte[]> selectContent(String objectKey) {
            return Optional.empty();
        }
    }

    /** トランザクションを実際には開かない管理者。 */
    private static final class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // DB を使わないので何もしない
        }

        @Override
        public void rollback(TransactionStatus status) {
            // DB を使わないので何もしない
        }
    }

    @Test
    void 保存した時刻はClockから取る() {
        RecordingMapper mapper = new RecordingMapper();
        MyBatisExternalPayloadStorage storage = new MyBatisExternalPayloadStorage(
                mapper, new NoOpTransactionManager(), Clock.fixed(NOW, ZoneOffset.UTC));

        storage.store(PayloadObjectKey.newRecordPayload(), new byte[] {1});

        assertThat(mapper.storedAts).containsExactly(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }
}
