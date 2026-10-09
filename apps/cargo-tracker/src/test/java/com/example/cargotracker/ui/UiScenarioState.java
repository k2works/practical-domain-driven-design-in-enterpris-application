package com.example.cargotracker.ui;

import io.cucumber.spring.ScenarioScope;

/**
 * 画面の層のシナリオの中で、ステップ定義の間に受け渡す値。シナリオごとに作り直す。
 * 荷主のステップが提出した見積依頼の業務番号を、営業担当者のステップが使う。営業担当者が確定した予約の追跡番号を、追跡管理者の
 * ステップが使う（Bolt 26）。
 */
@ScenarioScope
public class UiScenarioState {

    private String transportRequestNumber;
    private String trackingNumber;

    public String transportRequestNumber() {
        return transportRequestNumber;
    }

    public void transportRequestNumber(String transportRequestNumber) {
        this.transportRequestNumber = transportRequestNumber;
    }

    public String trackingNumber() {
        if (trackingNumber == null) {
            throw new IllegalStateException("追跡番号がありません。先に「予約の詳細を更新して追跡の開始が完了するのを待つ」を通してください");
        }
        return trackingNumber;
    }

    public void trackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
}
