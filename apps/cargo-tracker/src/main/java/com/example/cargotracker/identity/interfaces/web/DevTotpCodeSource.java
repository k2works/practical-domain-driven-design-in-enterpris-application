package com.example.cargotracker.identity.interfaces.web;

import java.util.Optional;

/**
 * 開発環境だけの A-02 の入力済みのコード（Bolt 31、ADR-011 の dev の 1・3 層目）。dev プロファイルでだけ部品になり、ほかの環境では
 * この部品自体がない。実装は {@code identity.infrastructure.devlogin} に置き、組み立ては {@code identity.infrastructure.config} が担う
 * （インターフェース層は infrastructure に依存しない。2026-10-09 の人の指示）。controller は TOTP の生成器や秘密を直接参照しない。
 */
@FunctionalInterface
public interface DevTotpCodeSource {

    /** 開発用の利用者のその時点のコード。開発用の利用者でなければ空。 */
    Optional<String> codeFor(String email);
}
