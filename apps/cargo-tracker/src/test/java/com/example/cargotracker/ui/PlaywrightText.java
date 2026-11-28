package com.example.cargotracker.ui;

import java.util.regex.Pattern;

/**
 * Playwright の照合に渡す正規表現の部品（Bolt 33c、Try T-128）。Playwright は Java の正規表現をブラウザーの JavaScript の正規表現に
 * 変えて照合するため、JavaScript にない {@code Pattern.quote} の {@code \Q…\E} は使えない（Bolt 5・25・33a で同じ落とし穴を踏んだ）。
 * 画面の層のステップで文字列を正規表現に入れるときは、この部品でエスケープする（{@code AcceptanceStepArchitectureTest} が
 * {@code Pattern.quote} を見張る）。
 */
final class PlaywrightText {

    private PlaywrightText() {}

    /** 正規表現のメタ文字だけをエスケープした文字列。 */
    static String literal(String text) {
        return text.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }

    /** 文字列で始まる正規表現（アクセシブルな名前・説明の先頭の一致に使う）。 */
    static Pattern startingWith(String text) {
        return Pattern.compile("^" + literal(text));
    }
}
