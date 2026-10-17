package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 監査記録の操作（データモデル `audit_record.action`）。Bolt 14 はログインの成功・失敗とログアウト。Bolt 31 で password の段の
 * 成功を分けた（ADR-012 の見直しの 1）。
 */
public enum AuditAction {
    /** password を確かめた（2 段階のログインの 1 段目。まだ業務の画面は開けない）。 */
    PASSWORD_VERIFIED,
    /** ログインに成功した（password と TOTP の 2 つの要素がそろった）。 */
    LOGIN_SUCCEEDED,
    /** ログインに失敗した。 */
    LOGIN_FAILED,
    /** TOTP を登録した（Bolt 31b）。 */
    TOTP_ENROLLED,
    /** 認証アプリを登録し直した（回復コードでログインした後。秘密を置き換え、回復コードを発行し直した。Bolt 33b）。 */
    TOTP_RE_ENROLLED,
    /** 回復コードを 1 つ使った（A-02 で TOTP の代わりに使った。Bolt 31c。続けて LOGIN_SUCCEEDED を書く）。 */
    RECOVERY_CODE_USED,
    /** 5 回続けて失敗してロックした（Bolt 32。US-18 AC3）。 */
    ACCOUNT_LOCKED,
    /** request ごとの確かめで利用停止・無効な企業・利用者なしを見つけて session を捨てた（Bolt 32c。US-18 AC6）。 */
    SESSION_REVOKED,
    /** ログアウトした。 */
    LOGOUT,
    /**
     * password の再設定を依頼した（Bolt 33。US-18 AC8）。有効な利用者なら成功、登録なし・利用停止・企業の無効なら失敗（理由は
     * {@link AuthenticationRejection} の値）。メールを送ったかは記録しない（通知の送信の記録で分かる）。
     */
    PASSWORD_RESET_REQUESTED,
    /**
     * password の再設定を終えた（Bolt 33a。US-18 AC8）。A-06 で新しい password を設定した。成功だけを記録する（方針の誤り・無効なリンクは
     * 記録しない）。
     */
    PASSWORD_RESET_COMPLETED,
    /**
     * システム管理者が利用者の役割を変えた（Bolt 34。US-16 AC1 の役割の分）。成功だけを記録し、対象（利用者と更新した後の版）と
     * 変更前後の役割を {@link AuditTarget} に残す。拒んだ変更と古い版からの変更は記録しない。
     */
    ROLES_CHANGED,
    /**
     * システム管理者が管理範囲外の利用者を開いた・変えようとした（Bolt 34b。US-16 AC2、IA-INV-14）。結果は失敗、理由は
     * {@code OUT_OF_SCOPE}、対象は利用者。1 回の request につき 1 行。
     */
    ADMIN_SCOPE_DENIED,
    /**
     * システム管理者が利用者の利用を停止した（Bolt 34d。US-16 AC5）。成功だけを記録し、理由に停止の理由（{@link SuspensionReason}）の
     * 名前を、対象（利用者と更新した後の版）と変更前後の利用状態を {@link AuditTarget} に残す。拒んだ変更と古い版からの変更は記録しない。
     */
    USER_SUSPENDED,
    /**
     * システム管理者が停止中の利用者の利用を再開した（Bolt 34d。US-16 AC5）。成功だけを記録し、理由は持たない。対象と変更前後の利用状態を
     * {@link AuditTarget} に残す。
     */
    USER_REACTIVATED
}
