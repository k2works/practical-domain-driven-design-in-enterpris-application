package com.example.cargotracker.identity.infrastructure.monitoring;

import com.example.cargotracker.identity.domain.model.aggregates.IncompleteEventPublicationCounter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 未完了のイベントの発行の件数（Spring Modulith のイベントの発行の記録 {@code platform.event_publication} の、完了時刻のない行を数える。
 * W9 の Bolt 44-2）。リスナーが処理中のものも、完了するまでは未完了に数える（一時的に 0 でないことがある。続けて 0 にならなければ再配信を確かめる）。
 * H2 と PostgreSQL の共通の構文で書く（ADR-007）。
 */
@Component
public class ModulithIncompleteEventPublicationCounter implements IncompleteEventPublicationCounter {

    private final JdbcClient jdbc;

    public ModulithIncompleteEventPublicationCounter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long count() {
        return jdbc
                .sql("SELECT COUNT(*) FROM platform.event_publication WHERE completion_date IS NULL")
                .query(Long.class)
                .list()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}
