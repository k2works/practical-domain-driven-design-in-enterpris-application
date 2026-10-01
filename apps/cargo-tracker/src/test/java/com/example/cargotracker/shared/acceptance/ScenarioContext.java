package com.example.cargotracker.shared.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * シナリオの中でステップ定義の間に受け渡す値。シナリオごとに作り直す。
 */
@Component
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
