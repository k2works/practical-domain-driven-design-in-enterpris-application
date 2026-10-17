package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 利用状態の変更を受け付けない理由（Bolt 34d。US-16 AC5、IA-INV-01・IA-INV-14）。判定はこの宣言の順。画面はこの種類で文言を選ぶ。
 * 画面に示す付随の値がないので、役割の変更の拒否（理由と役割の組）と違って種類だけを持つ。
 */
public enum StatusChangeRejection {
    /** 自分の利用状態を変えようとした。 */
    SELF_CHANGE,
    /** 監督の役割（システム管理者・監査担当者）を持つ利用者を止める・再開しようとした（画面では扱わない。運用の手順で行う）。 */
    OVERSIGHT_ROLE,
    /** 今と同じ利用状態への変更。 */
    NO_CHANGE,
    /** 停止の理由を選んでいない停止。 */
    REASON_REQUIRED
}
