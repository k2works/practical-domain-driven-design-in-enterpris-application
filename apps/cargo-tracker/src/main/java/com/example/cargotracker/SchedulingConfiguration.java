package com.example.cargotracker;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定期処理の有効化と排他（ShedLock。W10 の Bolt 53-1）。どのコンテキストにも属さない横断の取り決めなので、{@link ClockConfiguration}
 * と同じくルートに置く（アーキテクチャの規則の例外。2026-11-16、human:kakimomokuri が承認）。複数のインスタンスで動かしても、
 * 同じ名前の定期処理は同時に 1 つだけ動く。ロックの表は {@code platform.shedlock}（Flyway が作る）。時刻はデータベースの時刻で数え、
 * インスタンスの時計のずれに左右されない。上限（{@code defaultLockAtMostFor}）は、動いたインスタンスが落ちても次のインスタンスが
 * 取れるまでの最長の時間で、各定期処理が {@code @SchedulerLock} で名前と上限を決める。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfiguration {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("platform.shedlock")
                .usingDbTime()
                .build());
    }
}
