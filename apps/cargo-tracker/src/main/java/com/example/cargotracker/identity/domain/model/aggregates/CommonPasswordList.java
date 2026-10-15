package com.example.cargotracker.identity.domain.model.aggregates;

/**
 * よく使われる password の一覧。新しい password が一覧に載っているかを答えるポート（SEC-05。Bolt 33a）。実装は
 * {@code identity.infrastructure.crypto} の同梱の一覧。大文字小文字を区別せずに比べる。
 */
@FunctionalInterface
public interface CommonPasswordList {

    /** 一覧に載っているか（大文字小文字を区別しない）。 */
    boolean contains(String password);
}
