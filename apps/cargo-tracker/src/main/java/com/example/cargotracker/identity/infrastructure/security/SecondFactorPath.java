package com.example.cargotracker.identity.infrastructure.security;

/**
 * 2 つ目の要素の経路。認証の結果のトークン（{@link TotpAuthenticationToken}）が、TOTP と回復コードのどちらで通ったかを持つ（Bolt 33e。
 * 回復コードの経路を request の属性で成功の扱いに伝えていたのをやめた）。権限ではないので、要素の権限の名前（{@code FACTOR_}）にしない。
 */
public enum SecondFactorPath {
    /** 認証アプリのコード。 */
    TOTP,
    /** 回復コード（通ったら A-08 へ導く。US-18 AC9）。 */
    RECOVERY_CODE
}
