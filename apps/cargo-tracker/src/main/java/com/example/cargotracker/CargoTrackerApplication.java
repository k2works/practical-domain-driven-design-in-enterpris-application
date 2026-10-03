package com.example.cargotracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.modulith.Modulithic;

/**
 * A 社国際貨物輸送管理システム（cargo-tracker）。境界づけられたコンテキストをモジュールに分けたモジュラーモノリス（ADR-001）。
 *
 * <p>{@code @SpringBootApplication} の部品探索に application の除外を足している。アプリケーションサービスの
 * {@code @Service} は JIG がユースケースとして読むための印で、組み立ては各コンテキストの infrastructure.config の
 * {@code @Bean} だけが担う（バックエンドアーキテクチャ、2026-10-03 の人の決定）。
 * Spring Modulith が実行時にルートを見つけられるよう、{@code @SpringBootApplication} は外さない。
 */
@Modulithic
@SpringBootApplication
@ComponentScan(
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
            @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.example\\.cargotracker\\..+\\.application\\..+")
        })
@ConfigurationPropertiesScan
public class CargoTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CargoTrackerApplication.class, args);
    }
}
