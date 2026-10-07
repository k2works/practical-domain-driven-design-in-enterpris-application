package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

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

    public DecisionRationale {
        Objects.requireNonNull(text, "text");
        if (text.isBlank() || !text.equals(text.strip())) {
            throw new IllegalArgumentException("判断根拠は前後の空白を除いた 1 文字以上です");
        }
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("判断根拠は " + MAX_LENGTH + " 文字までです");
        }
    }
}
