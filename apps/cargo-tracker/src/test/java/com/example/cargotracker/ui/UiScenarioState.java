package com.example.cargotracker.ui;

import io.cucumber.spring.ScenarioScope;

/**
 * 画面の層のシナリオの中で、ステップ定義の間に受け渡す値。シナリオごとに作り直す。
 * 荷主のステップが提出した見積依頼の業務番号を、営業担当者のステップが使う。
 */
@ScenarioScope
public class UiScenarioState {

    private String transportRequestNumber;

    public String transportRequestNumber() {
        return transportRequestNumber;
    }

    public void transportRequestNumber(String transportRequestNumber) {
        this.transportRequestNumber = transportRequestNumber;
    }
}
