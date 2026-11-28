package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 担当範囲の変更を受け付けない理由（Bolt 38e。US-16 AC1、IA-INV-02・IA-INV-14）。判定はこの宣言の順。画面はこの種類で文言を選ぶ。画面に
 * 示す付随の値がないので、利用状態の変更の拒否と同じく種類だけを持つ。
 */
public enum AssignmentChangeRejection {
    /** 担当範囲のある役割（営業担当者・経路設計者・追跡管理者・カスタマーサポート）を持たない利用者（画面からは来ない）。 */
    NOT_ASSIGNMENT_SCOPED,
    /** 選べない企業（操作者の管理範囲の外、荷主でない、無効）を選んだ。 */
    UNSELECTABLE_COMPANY,
    /** 選べる企業の中で、今の担当企業から変わっていない。 */
    NO_CHANGE
}
