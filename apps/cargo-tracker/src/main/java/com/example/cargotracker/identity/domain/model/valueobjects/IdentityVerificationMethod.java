package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 本人確認の手段。システム管理者が利用者の TOTP の登録を解除する前に行った、事前に決めた別経路の本人確認（Bolt 38c。US-18 AC9、SEC-10）。
 * 監査記録 {@code TOTP_RESET_BY_ADMIN} の変更後に名前を残す。自由な文は持たない（2026-10-27、human:kakimomokuri）。表示名は画面の層が持つ。
 */
@ValueObject
public enum IdentityVerificationMethod {
    /** 登録の電話番号への折り返し。 */
    PHONE_CALLBACK,
    /** 対面。 */
    IN_PERSON,
    /** 所属企業の管理担当者への確認。 */
    COMPANY_ADMINISTRATOR
}
