package com.example.cargotracker.ui;

import com.example.cargotracker.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 画面の層の受入シナリオの組み立て（テスト戦略「シナリオの階層」）。
 * アプリケーションをランダムなポートで起動し、PostgreSQL 18.6（Testcontainers）を使う。
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, BrowserSession.class})
public class UiTestConfiguration {}
