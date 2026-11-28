package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;
import java.util.Optional;

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
        if (!text.equals(text.strip())) {
            throw new IllegalArgumentException("前後の空白を除いた値を渡してください");
        }
        rejectionOf(text).ifPresent(reason -> {
            throw new IllegalArgumentException("判断根拠にならない値です: " + reason);
        });
    }

    /** 入力が判断根拠にならない理由（なれば空）。 */
    public static Optional<RouteConfirmationRejectionReason> rejectionOf(String input) {
        if (input == null || input.isBlank()) {
            return Optional.of(RouteConfirmationRejectionReason.RATIONALE_MISSING);
        }
        String text = input.strip();
        // 文字はコードポイントで数える（表の VARCHAR(4000) と同じ数え方。サロゲートペアは 1 文字）
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            return Optional.of(RouteConfirmationRejectionReason.RATIONALE_TOO_LONG);
        }
        return Optional.empty();
    }

    /** 入力から判断根拠を作る（前後の空白を除く）。 */
    public static DecisionRationale of(String input) {
        return new DecisionRationale(Objects.requireNonNull(input, "input").strip());
    }
}
