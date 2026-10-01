package com.example.cargotracker;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 現在時刻は Clock から得る（バックエンドアーキテクチャの横断の取り決め）。業務上の時刻は UTC で扱う（BR-10）。
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
