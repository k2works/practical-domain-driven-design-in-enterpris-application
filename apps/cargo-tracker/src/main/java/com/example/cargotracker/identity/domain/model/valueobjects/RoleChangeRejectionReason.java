package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 役割の変更を受け付けない理由（Bolt 34。US-16 AC3、UC-16 の主シナリオ 4）。判定はこの宣言の順に行う。
 */
public enum RoleChangeRejectionReason {
    /** 操作者が自分の役割を変えようとした（Bolt 34 の開始準備の決定 4）。 */
    SELF_CHANGE,
    /** 役割を 1 つも選んでいない（利用者には役割が 1 つ以上要る。BR-15）。 */
    NO_ROLES,
    /** 今の役割から変わっていない。 */
    NO_CHANGE,
    /** 所属企業の種類に付けられない役割がある（IA-INV-13）。 */
    UNFIT_FOR_COMPANY,
    /** 職務分離で競合する役割がある（IA-INV-01）。 */
    CONFLICTING_ROLES
}
