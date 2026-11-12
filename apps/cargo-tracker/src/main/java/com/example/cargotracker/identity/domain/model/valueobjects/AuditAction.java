package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 監査記録の操作（データモデル `audit_record.action`）。Bolt 14 はログインの成功・失敗とログアウト。Bolt 31 で password の段の
 * 成功を分けた（ADR-012 の見直しの 1）。Bolt 39b3b で、ドメインイベントを経て書く外部原本の採否（採用と隔離）を足した（IA-INV-15）。
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
    /**
     * システム管理者が別経路の本人確認をしたうえで利用者の TOTP の登録を解除した（Bolt 38c。US-18 AC9、SEC-10）。成功だけを記録し、理由は
     * {@code IDENTITY_VERIFIED}、対象（利用者と今の版）と、変更前 {@code ENROLLED}・変更後に本人確認の手段の名前と日時（UTC）を
     * {@link AuditTarget} に残す。既に解除・登録が変わったときは記録しない。
     */
    TOTP_RESET_BY_ADMIN,
    /** 回復コードを 1 つ使った（A-02 で TOTP の代わりに使った。Bolt 31c。続けて LOGIN_SUCCEEDED を書く）。 */
    RECOVERY_CODE_USED,
    /** 5 回続けて失敗してロックした（Bolt 32。US-18 AC3）。 */
    ACCOUNT_LOCKED,
    /**
     * システム管理者がロック中の利用者のロックを解除した（Bolt 38c。US-18 AC3 の運用の側）。成功だけを記録し、理由は持たない。対象
     * （利用者と今の版）とロックの前後（{@code LOCKED}・{@code UNLOCKED}）を {@link AuditTarget} に残す。既に解除されていたときは記録しない。
     */
    ACCOUNT_UNLOCKED,
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
     * 招待のリンクで初回の password を設定した（Bolt 38b4、US-16 AC1・US-18 AC7）。A-06 で初回の password を設定し、招待のトークンを使ったとき。
     * 成功だけを記録する（方針の誤り・使えないリンクは記録しない）。再設定のリンクで初めて設定した場合は {@link #PASSWORD_RESET_COMPLETED}。
     */
    INITIAL_PASSWORD_SET,
    /**
     * システム管理者が利用者の役割を変えた（Bolt 34。US-16 AC1 の役割の分）。成功だけを記録し、対象（利用者と更新した後の版）と
     * 変更前後の役割を {@link AuditTarget} に残す。拒んだ変更と古い版からの変更は記録しない。
     */
    ROLES_CHANGED,
    /**
     * システム管理者が管理範囲外の利用者を開いた・変えようとした（Bolt 34b。US-16 AC2、IA-INV-14）、または管理範囲外の企業に利用者を
     * 登録しようとした（Bolt 38b2）。結果は失敗、理由は {@code OUT_OF_SCOPE}、対象は利用者または企業。1 回の request につき 1 行。
     */
    ADMIN_SCOPE_DENIED,
    /**
     * 業務担当者が担当企業の外の確定の操作を送った（担当範囲の外の試行。Bolt 38f。US-16 AC4、IA-INV-08、ADR-019）。結果は失敗、理由は
     * {@code OUT_OF_SCOPE}、対象は業務の対象（輸送要求・見積りなど。種類と ID）。版と変更前後はない。1 回の request につき 1 行。管理範囲の
     * 外の試行（{@link #ADMIN_SCOPE_DENIED}）とは別の値。
     */
    ASSIGNED_SCOPE_DENIED,
    /**
     * システム管理者が利用者を登録した（Bolt 38b2。US-16 AC1）。成功だけを記録し、対象（新しい利用者と版 0）と登録した役割を
     * {@link AuditTarget} に残す。メールアドレスと表示名は書かない。拒んだ登録は記録しない（範囲外の企業への試行は {@link #ADMIN_SCOPE_DENIED}）。
     */
    USER_REGISTERED,
    /**
     * システム管理者が利用者の利用を停止した（Bolt 34d。US-16 AC5）。成功だけを記録し、理由に停止の理由（{@link SuspensionReason}）の
     * 名前を、対象（利用者と更新した後の版）と変更前後の利用状態を {@link AuditTarget} に残す。拒んだ変更と古い版からの変更は記録しない。
     */
    USER_SUSPENDED,
    /**
     * システム管理者が停止中の利用者の利用を再開した（Bolt 34d。US-16 AC5）。成功だけを記録し、理由は持たない。対象と変更前後の利用状態を
     * {@link AuditTarget} に残す。
     */
    USER_REACTIVATED,
    /**
     * システム管理者が業務担当者の担当範囲（担当企業）を変えた（Bolt 38e。US-16 AC1 の担当の関係と変更履歴）。成功だけを記録し、対象
     * （利用者と更新した後の版）と変更前後の担当企業（管理範囲の外を含む全体を企業 ID の昇順にカンマでつないだ一覧）を {@link AuditTarget}
     * に残す。企業名は変わりうるので残さない。拒んだ変更と古い版からの変更は記録しない。
     */
    ASSIGNMENTS_CHANGED,
    /**
     * 外部原本を採用した（取込での自動の採用と、データ責任者の採用の判断。DE-12・DE-13 を監査する。Bolt 39b3b・39d3b、AUD-01、IA-INV-15）。成功だけを記録し、理由は持たない。操作者と
     * 時刻はイベントのもの、対象は受信記録（{@code RECEIVED_RECORD}、受信記録 ID。版と変更前は持たない）、変更後は正規の要約。
     */
    EXTERNAL_RECORD_ADOPTED,
    /**
     * 外部原本を隔離した（DE-14 を監査する。Bolt 39b3b、AUD-01、IA-INV-15）。成功だけを記録し、理由は隔離の理由の区分
     * （{@code CONTENT_MISMATCH} か形式の誤りの名前）。対象と変更後は {@link #EXTERNAL_RECORD_ADOPTED} と同じ形。
     */
    EXTERNAL_RECORD_QUARANTINED,
    /**
     * 外部原本の差異を見つけた（DE-31 を監査する。Bolt 39h、AUD-01、IA-INV-15）。成功だけを記録し、理由は差異の理由の区分
     * （{@code VERSION_REVERSED} か {@code TARGET_KEY_DIFFERS}）。隔離と別の操作にし、差異を隔離として数えない。対象と変更後は
     * {@link #EXTERNAL_RECORD_QUARANTINED} と同じ形で、変更後の要約に比べた外部版を入れる。
     */
    EXTERNAL_RECORD_DISCREPANCY_FOUND,
    /**
     * データ責任者が受信記録を判断した（DE-29 を監査する。Bolt 39d2、AUD-01、IA-INV-15）。人の判断として成功だけを記録し、理由は判断
     * （{@code REJECTED} か {@code ADOPTED}。採用は Bolt 39d3b）。判断の理由の文は 100 文字の理由の列に収まらないので、あれば変更後の要約の最後に置き、理由のない採用は要約の {@code reason} の部品を省く。対象は
     * {@link #EXTERNAL_RECORD_ADOPTED} と同じ形。
     */
    EXTERNAL_RECORD_DECIDED
}
