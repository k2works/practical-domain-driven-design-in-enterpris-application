package com.example.cargotracker.ui;

import io.cucumber.spring.ScenarioScope;
import java.util.Locale;
import java.util.UUID;

/**
 * 画面の層のシナリオの中で、ステップ定義の間に受け渡す値。シナリオごとに作り直す。
 * 荷主のステップが提出した見積依頼の業務番号を、営業担当者のステップが使う。営業担当者が確定した予約の追跡番号を、追跡管理者の
 * ステップが使う（Bolt 26）。
 */
@ScenarioScope
public class UiScenarioState {

    private final String voyageMark =
            UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    private boolean voyagesWritten;
    private String transportRequestNumber;
    private String trackingNumber;

    /** シナリオごとに一意の航海番号（30 文字の上限の内。取込の Steps と経路設計の Steps で同じ番号を使う。Bolt 39e の決定 (12)）。 */
    public String voyageNumber(String name) {
        return "UI-" + voyageMark + "-" + name;
    }

    /** このシナリオの航海番号の LIKE の形（後片付け）。 */
    public String voyageNumberPattern() {
        return "UI-" + voyageMark + "-%";
    }

    /** 航海を入れた（取り込んだ）印を付ける。印を付けたシナリオだけ航海を後片付けする。 */
    public void markVoyagesWritten() {
        voyagesWritten = true;
    }

    public boolean voyagesWritten() {
        return voyagesWritten;
    }

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
