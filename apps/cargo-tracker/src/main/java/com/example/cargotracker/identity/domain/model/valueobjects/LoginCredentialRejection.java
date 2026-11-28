package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * ログインの資格の変更（システム管理者によるロックの解除と TOTP の解除）を受け付けない理由（Bolt 38c。US-18 AC3・AC9、IA-INV-01・
 * IA-INV-14）。判定はこの宣言の順。自分と監督の役割は利用者の集約が、ほかはサービスが今の状態で判定する。画面はこの種類で文言を選ぶ。
 */
public enum LoginCredentialRejection {
    /** 自分のログインの資格を変えようとした。 */
    SELF,
    /** 監督の役割（システム管理者・監査担当者）を持つ利用者の資格を変えようとした（画面では扱わない。運用の手順で行う）。 */
    OVERSIGHT_ROLE,
    /** ロック中でない利用者のロックを解除しようとした（期限が切れた・ほかの管理者が先に解除した）。 */
    NOT_LOCKED,
    /** 認証アプリを登録していない利用者の登録を解除しようとした（ほかの管理者が先に解除した・もともと未登録）。 */
    NOT_ENROLLED,
    /** 確認の画面で読んだ登録の後に、本人が登録し直した（新しい登録を消さない）。 */
    ENROLLMENT_CHANGED
}
