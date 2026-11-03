package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 物理行の範囲。取込ファイルの 1 行（引用符の中の改行で複数の物理行にまたがる）か誤りが、ファイルの物理行のどこからどこまでかを表す
 * （Bolt 39b2 の開発レビュー）。受け取った件数（物理データ行の数）との突き合わせに使う物理行の数と、隔離の受信記録の注記の範囲の言葉を持つ。
 *
 * @param first 始まりの物理行の番号（1 から）
 * @param last 終わりの物理行の番号（始まり以上）
 */
@ValueObject
public record PhysicalLineRange(int first, int last) {

    public PhysicalLineRange {
        if (first < 1) {
            throw new IllegalArgumentException("物理行の番号は 1 以上: " + first);
        }
        if (last < first) {
            throw new IllegalArgumentException("物理行の範囲の終わりが始まりより前: " + first + ".." + last);
        }
    }

    /** 物理行の数（始まりと終わりを含む）。 */
    public int lineCount() {
        return last - first + 1;
    }

    /** 注記の範囲の言葉（「12 行目」「12 行目から 13 行目まで」）。 */
    public String wording() {
        return first == last ? first + " 行目" : first + " 行目から " + last + " 行目まで";
    }
}
