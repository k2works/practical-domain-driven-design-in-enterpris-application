package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.util.Optional;

/**
 * 表示名の規則。利用者の登録の表示名を正規化して確かめる（Bolt 38b3。Bolt 38b3b で登録のサービスから移した）。前後の空白（NBSP などの
 * Unicode の空白を含む）を除き、空・200 コードポイントを超える・見分けにくい文字（制御文字・書式文字・行と段落の区切り）を含む名前は
 * 受け付けない（一覧と詳細で見分けにくい名前を登録させない）。登録のサービスの判定と、登録の画面の確認の領域（保存する値を示す）が使う。
 */
@DomainRule
public final class DisplayNameRule {

    /** 表示名の長さの上限（コードポイント。`app_user.display_name` は `VARCHAR(200)`）。 */
    private static final int MAX_CODE_POINTS = 200;

    private DisplayNameRule() {}

    /**
     * 前後の空白を除いた表示名。規則に合わなければ空。
     *
     * @param displayName 入れたままの表示名（null は空として扱う）
     */
    public static Optional<String> normalize(String displayName) {
        String name = stripSpaces(displayName == null ? "" : displayName);
        boolean valid = !name.isEmpty()
                && name.codePointCount(0, name.length()) <= MAX_CODE_POINTS
                && name.codePoints().noneMatch(DisplayNameRule::isIndistinct);
        return valid ? Optional.of(name) : Optional.empty();
    }

    private static String stripSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isIndistinct(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
