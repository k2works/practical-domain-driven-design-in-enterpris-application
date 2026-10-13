package com.example.cargotracker.identity.domain.model.aggregates;

import java.io.Serial;

/**
 * 読めない認証の要素。保存した TOTP の秘密を復号できない（知らない鍵の版・壊れた暗号文・別の利用者の暗号文）か、保存した回復コードの
 * ハッシュの形が違う（Bolt 31d、ADR-011 の決定 8）。利用者の入力の誤りではないが、ログインは通さずに拒否へ倒す（T-42）。認証の
 * 要素のポート（{@link TotpCodeGenerator}・{@link TotpSecretIssuer}・{@link RecoveryCodeHasher}）の実装が投げ、認証の入口と登録の
 * サービスはこの例外だけを捕まえる。ほかの {@link IllegalArgumentException} は実装の誤りとして捕まえない（500 にしてログに残す）。
 * 引数の誤りでなく保存したデータの状態なので {@link RuntimeException} を継ぐ（どこかで {@code IllegalArgumentException} を捕まえても
 * 拒否の範囲が黙って元に戻らない。Bolt 32 で {@code IllegalArgumentException} から改めた）。
 */
public final class UnreadableCredentialException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnreadableCredentialException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnreadableCredentialException(String message) {
        super(message);
    }
}
