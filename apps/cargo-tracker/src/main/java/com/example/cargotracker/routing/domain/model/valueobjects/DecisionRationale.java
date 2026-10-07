package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 判断根拠。経路設計者が経路を確定するときに記録する、その候補を選んだ理由（R-INV-04、US-07 AC1・AC2。Bolt 19）。
 * 前後の空白を除いて 1〜4,000 文字。
 *
 * @param text 本文（前後の空白を除いたもの）
 */
@ValueObject
public record DecisionRationale(String text) {

    /** 本文の上限の文字数（審査の根拠 Q-INV-14 と同じ）。 */
    public static final int MAX_LENGTH = 4000;
}
