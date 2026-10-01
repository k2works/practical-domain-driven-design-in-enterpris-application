package com.example.cargotracker.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * 業務ルール層の受入シナリオの組み立て（テスト戦略）。
 * DB とアプリケーション全体を起動せず、入力ポートの実装・メモリ上のリポジトリ・固定の Clock・
 * テスト用の同期のイベント配信で組み立てる。
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = AcceptanceTestConfiguration.Components.class)
public class AcceptanceTestConfiguration {

    @Configuration(proxyBeanMethods = false)
    static class Components {
    }
}
