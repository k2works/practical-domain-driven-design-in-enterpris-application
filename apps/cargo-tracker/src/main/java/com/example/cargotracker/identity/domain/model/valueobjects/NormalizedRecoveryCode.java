package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 正規化した回復コード。小文字の 16 進 8 文字を 4 文字ずつハイフンで区切った形（{@code 1a2b-3c4d}。32 ビット）で、発行の形と
 * 照合の形を兼ねる（Bolt 33d）。回復コードの形の知識はここだけに置き、作るときに形を確かめるので、形の合わない値は照合に渡らない。
 * 入力の正規化（空白・大文字・全角）は {@code RecoveryCode.normalize} が行う。
 */
@ValueObject
public record NormalizedRecoveryCode(String value) {

    /** 回復コードを作る乱数のバイト数（32 ビット）。 */
    public static final int BYTES = 4;

    private static final Pattern FORM = Pattern.compile("[0-9a-f]{4}-[0-9a-f]{4}");

    /** 区切りのハイフンを入れ忘れた形（8 文字の 16 進）。 */
    private static final Pattern UNHYPHENATED = Pattern.compile("[0-9a-f]{8}");

    private static final HexFormat HEX = HexFormat.of();

    public NormalizedRecoveryCode {
        if (value == null || !FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("回復コードの形ではない");
        }
    }

    /**
     * 空白を除き小文字にした文字列を回復コードとして読む。区切りのハイフンを入れ忘れた 8 文字は 4 文字目の後に入れる。形が合わなければ
     * 空にする（入力の空白・大文字・全角は {@code RecoveryCode.normalize} が先に直す）。
     */
    public static Optional<NormalizedRecoveryCode> parse(String compact) {
        if (compact == null) {
            return Optional.empty();
        }
        String hyphenated = UNHYPHENATED.matcher(compact).matches()
                ? compact.substring(0, 4) + "-" + compact.substring(4)
                : compact;
        return FORM.matcher(hyphenated).matches()
                ? Optional.of(new NormalizedRecoveryCode(hyphenated))
                : Optional.empty();
    }

    /** 乱数のバイト（{@value #BYTES} バイト）から回復コードを作る（発行の形）。 */
    public static NormalizedRecoveryCode fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != BYTES) {
            throw new IllegalArgumentException("回復コードは " + BYTES + " バイトから作る");
        }
        String hex = HEX.formatHex(bytes);
        return new NormalizedRecoveryCode(hex.substring(0, 4) + "-" + hex.substring(4));
    }

    @Override
    public String toString() {
        // 平文の回復コードをログに出さない
        return "NormalizedRecoveryCode[****]";
    }
}
