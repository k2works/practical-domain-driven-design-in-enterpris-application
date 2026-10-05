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

    /** 提出した輸送要求の業務番号（例: TR-2026-0001）。コンテキストをまたいで渡すため文字列で持つ。 */
    private String transportRequestNumber;

    public UUID transportRequestId() {
        return transportRequestId;
    }

    public void transportRequestId(UUID transportRequestId) {
        this.transportRequestId = transportRequestId;
    }

    public String transportRequestNumber() {
        return transportRequestNumber;
    }

    public void transportRequestNumber(String transportRequestNumber) {
        this.transportRequestNumber = transportRequestNumber;
    }
}
