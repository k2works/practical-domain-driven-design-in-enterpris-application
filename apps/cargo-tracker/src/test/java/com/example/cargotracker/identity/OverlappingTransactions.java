package com.example.cargotracker.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 2 つの操作を別々のトランザクションで重ねて動かすテストの補助（Bolt 30 の T-100、Bolt 31 で抜き出した）。
 * 先の操作はコミットを止め、後の操作がその行ロックを待っている（終わらない）ことを確かめてから、先をコミットする。
 * 開始のラッチだけで組むと、2 つが直列に動いても通ってしまう（Bolt 30 のふりかえり）。「一定時間の間に終わらない」だけでは、
 * 遅いだけの操作と区別できないので、後の操作の接続が先の接続を待っていることを PostgreSQL の {@code pg_blocking_pids} で
 * 確かめる（Bolt 32d。PostgreSQL の統合テストだけが使う）。接続はトランザクションマネージャーの {@code DataSource} から取る。
 */
public final class OverlappingTransactions {

    private static final long TIMEOUT_SECONDS = 30;
    private static final long BLOCKED_CHECK_MILLIS = 500;
    private static final long LOCK_WAIT_POLL_MILLIS = 50;

    private OverlappingTransactions() {}

    /** 先・後の順に結果を返す。 */
    public static <T> List<T> run(TransactionTemplate transactionTemplate, Supplier<T> first, Supplier<T> second) {
        return overlap(transactionTemplate, first, () -> transactionTemplate.execute(status -> second.get()));
    }

    /**
     * 後の操作をトランザクションで包まずに動かす（後の操作が自分でトランザクションを持つとき。Bolt 32 の最初の失敗の作成の競合）。
     * 後をトランザクションで包むと、PostgreSQL では一意制約の違反で包んだトランザクションが中断される（ADR-007）。
     */
    public static <T> List<T> runSecondOutsideTransaction(
            TransactionTemplate transactionTemplate, Supplier<T> first, Supplier<T> second) {
        return overlap(transactionTemplate, first, second);
    }

    private static <T> List<T> overlap(TransactionTemplate transactionTemplate, Supplier<T> first, Supplier<T> second) {
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        JdbcTemplate jdbc = new JdbcTemplate(dataSourceOf(transactionTemplate));
        AtomicInteger firstPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<T> firstResult = CompletableFuture.supplyAsync(
                    () -> transactionTemplate.execute(status -> {
                        T result = first.get();
                        // 先のトランザクションの接続の番号（同じトランザクションの接続で読む）
                        firstPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        firstUpdated.countDown();
                        awaitQuietly(releaseFirst);
                        return result;
                    }),
                    executor);
            awaitQuietly(firstUpdated);
            CompletableFuture<T> secondResult = CompletableFuture.supplyAsync(second, executor);

            assertThat(secondResult)
                    .as("後の操作は先の行ロックを待つ")
                    .failsWithin(Duration.ofMillis(BLOCKED_CHECK_MILLIS))
                    .withThrowableOfType(TimeoutException.class);
            awaitLockWaitOn(jdbc, firstPid.get());
            releaseFirst.countDown();

            return List.of(
                    firstResult.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join(),
                    secondResult.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static DataSource dataSourceOf(TransactionTemplate transactionTemplate) {
        if (transactionTemplate.getTransactionManager() instanceof DataSourceTransactionManager manager) {
            return manager.getDataSource();
        }
        throw new IllegalStateException("DataSource のトランザクションマネージャーで使う");
    }

    /** 先の接続を待っている接続が現れるまで待つ（タイムアウトで落とす）。待ちは別の接続（自動コミット）で読む。 */
    private static void awaitLockWaitOn(JdbcTemplate jdbc, int blockingPid) {
        await("後の操作の接続が先の接続のロックを待っている（遅いだけでない）")
                .atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
                .pollInterval(Duration.ofMillis(LOCK_WAIT_POLL_MILLIS))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                                Integer.class,
                                blockingPid)
                        > 0);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("同時の操作がそろいませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
