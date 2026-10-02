package com.example.cargotracker.ui;

import com.example.cargotracker.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 画面の層の受入シナリオの組み立て（テスト戦略「シナリオの階層」）。
 * アプリをランダムなポートで起動し、PostgreSQL 18.6（Testcontainers）を使う。
 * アプリはアプリケーション利用者で接続し、Flyway は所有者で動かす。利用者がなければマイグレーションを失敗させる。
 */
@CucumberContextConfiguration
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.placeholders.requireappuser=true")
@Import({
    TestcontainersConfiguration.class,
    ApplicationUserConnection.class,
    PlaywrightBrowser.class,
    BrowserSession.class,
    UiScenarioState.class
})
public class UiTestConfiguration {}
