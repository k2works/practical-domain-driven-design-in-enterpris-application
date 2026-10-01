package com.example.cargotracker.shared.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.UUID;

/**
 * シナリオの中でステップ定義の間に受け渡す値。シナリオごとに作り直す。
 * 受入シナリオの組み立てが {@code @Import} で登録する（部品探索の対象にしない）。
 */
@ScenarioScope
public class ScenarioContext {

    private UUID transportRequestId;

    public UUID transportRequestId() {
        return transportRequestId;
    }

    public void transportRequestId(UUID transportRequestId) {
        this.transportRequestId = transportRequestId;
    }
}
