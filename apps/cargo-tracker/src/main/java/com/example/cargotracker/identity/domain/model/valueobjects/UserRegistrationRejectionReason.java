package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 利用者の登録を受け付けない理由（Bolt 38b。US-16 AC1・AC3、UC-16 の主シナリオ 1・4）。判定はこの宣言の順に行う（役割の変更の判定と
 * 同じ順。登録には自分の変更と変更なしがない）。
 */
public enum UserRegistrationRejectionReason {
    /** 役割を 1 つも選んでいない（利用者には役割が 1 つ以上要る。BR-15）。 */
    NO_ROLES,
    /** 監督の役割（システム管理者・監査担当者）を付けようとした。登録でも付けない（IA-INV-01。最初の管理者は運用の手順で作る）。 */
    OVERSIGHT_ROLE,
    /** 所属企業の種類に付けられない役割がある（IA-INV-13。荷受人の企業にはどの業務の役割も付けない）。 */
    UNFIT_FOR_COMPANY,
    /**
     * 職務分離で競合する役割がある（IA-INV-01）。今の規則では監督の役割を先に拒むので当たる組み合わせは残らないが、規則が広がったときに
     * 登録だけが黙って見なくならないよう、判定の最後に残す。
     */
    CONFLICTING_ROLES
}
