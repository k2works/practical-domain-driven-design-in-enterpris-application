package com.example.cargotracker;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 現在時刻は Clock から得る（バックエンドアーキテクチャの横断の取り決め）。業務上の時刻は UTC で扱う（BR-10）。
 * TIMESTAMP WITH TIME ZONE の精度に合わせてマイクロ秒に切り捨て、境界値の判定が保存の前後で変わらないようにする（ADR-007）。
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    private static final Duration MICROSECOND = Duration.ofNanos(1_000);

    @Bean
    Clock clock() {
        return microsecondClock(Clock.systemUTC());
    }

    static Clock microsecondClock(Clock base) {
        return Clock.tick(base, MICROSECOND);
    }
}
