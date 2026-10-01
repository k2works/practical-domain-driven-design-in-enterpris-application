package com.example.cargotracker.ui;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * 画面の層の受入シナリオ（{@code @ui}）を実行する。アプリケーションを同じ JVM で起動し（PostgreSQL は Testcontainers）、
 * Playwright の Chromium で実ブラウザを操作する（テスト戦略）。{@code ./gradlew uiTest} で動かす。
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.cargotracker.ui")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@ui and not @wip")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber-ui/cucumber.html, json:build/reports/cucumber-ui/cucumber.json")
public class RunUiCucumberTest {}
