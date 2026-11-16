package com.example.cargotracker.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 定期処理の排他（ShedLock。W10 の Bolt 53-1、データモデルの `platform.shedlock`）。複数のインスタンスが同じ定期処理を同時に
 * 動かさない。本物の PostgreSQL の表（Flyway が作る）に対して、同じ名前のロックを 2 回取り、2 回目が取れないことと、
 * 解放の後は取れることを確かめる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShedLockIntegrationTest {

    @Autowired
    LockProvider lockProvider;

    private static LockConfiguration lock(String name) {
        return new LockConfiguration(java.time.Instant.now(), name, Duration.ofMinutes(5), Duration.ZERO);
    }

    @Test
    void 同じ名前のロックは同時に1つしか取れず_解放の後は取れる() {
        String name = "test-" + UUID.randomUUID();

        Optional<SimpleLock> first = lockProvider.lock(lock(name));
        Optional<SimpleLock> second = lockProvider.lock(lock(name));

        assertThat(first).isPresent();
        assertThat(second).as("別のインスタンスがロックを持っている間は取れない").isEmpty();

        first.orElseThrow().unlock();

        assertThat(lockProvider.lock(lock(name))).as("解放の後は取れる").isPresent();
    }

    @Test
    void 違う名前のロックは互いに待たない() {
        Optional<SimpleLock> a = lockProvider.lock(lock("test-a-" + UUID.randomUUID()));
        Optional<SimpleLock> b = lockProvider.lock(lock("test-b-" + UUID.randomUUID()));

        assertThat(a).isPresent();
        assertThat(b).isPresent();
    }
}
