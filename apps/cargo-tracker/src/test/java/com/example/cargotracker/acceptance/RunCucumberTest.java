package com.example.cargotracker.acceptance;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * 業務ルール層の受入シナリオ（日本語 Gherkin）を実行する。作業中の {@code @wip} は必須の実行から外す（テスト戦略）。
 * 画面の層（{@code @ui}）は {@code RunUiCucumberTest} が別の組み立てで実行するため、ここでは外す。
 * グルーは業務ルール層のパッケージに絞る（Cucumber の Spring 連携は、1 つのグルーに文脈の設定を 1 つしか許さない）。
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
        key = GLUE_PROPERTY_NAME,
        value = "com.example.cargotracker.acceptance,com.example.cargotracker.shared.acceptance,"
                + "com.example.cargotracker.quotation.acceptance,com.example.cargotracker.identity.acceptance")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip and not @ui")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber.html, json:build/reports/cucumber/cucumber.json")
public class RunCucumberTest {}
