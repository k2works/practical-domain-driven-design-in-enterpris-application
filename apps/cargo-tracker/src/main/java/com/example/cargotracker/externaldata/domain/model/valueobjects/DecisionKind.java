package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 判断の種類。隔離・差異の受信記録に対するデータ責任者の判断（data_model の {@code received_record_decision.decision}。Bolt 39d2）。
 * quotation の {@code ReviewDecision} と読み違えないよう {@code Decision} だけの名前を避けた（Bolt 39d2 の開始準備の決定 (12)）。
 */
@ValueObject
public enum DecisionKind {
    /** 採用（Bolt 39d3b）。 */
    ADOPTED,
    /** 却下（Bolt 39d2）。 */
    REJECTED
}
