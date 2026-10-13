package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * TOTP の登録の表示。登録の画面に示す値（Bolt 31b、UI 設計「A-07」）。認証アプリが読む otpauth の URI、手で入力するキー（4 文字ずつ区切った
 * Base32）、QR コードのインラインの SVG。どれも秘密と同じ重さなので、登録の画面に一度だけ渡し、文字列にしても値を出さない
 * （記録への漏れを防ぐ。session にも置かない）。ZXing と平文の秘密を infrastructure に閉じるため、ポート（{@code TotpSecretIssuer}）
 * が表示の値として返す（ADR-011）。
 *
 * @param otpauthUri 認証アプリが読む URI
 * @param manualKey QR コードを使えないときに手で入れるキー
 * @param qrSvg QR コードのインラインの SVG（自分で作った SVG で、利用者の入力を含まない）
 */
@ValueObject
public record TotpProvisioning(String otpauthUri, String manualKey, String qrSvg) {

    public TotpProvisioning {
        requireText(otpauthUri, "otpauthUri");
        requireText(manualKey, "manualKey");
        requireText(qrSvg, "qrSvg");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("登録の表示の値がない: " + name);
        }
    }

    @Override
    public String toString() {
        return "TotpProvisioning[***]";
    }
}
