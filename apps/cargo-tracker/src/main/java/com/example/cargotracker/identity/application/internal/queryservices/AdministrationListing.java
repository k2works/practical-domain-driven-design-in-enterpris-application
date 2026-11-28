package com.example.cargotracker.identity.application.internal.queryservices;

import java.util.List;
import java.util.function.Supplier;

/**
 * 利用者の管理の一覧系の照会の結果（Bolt 38e。#50 (b)）。詳細の照会（{@link UserLookup}）と同じく、権限がなければ値で返し、画面の層が
 * 403（A-04）にする。アプリケーション層に Spring Security の例外を持ち込まない（開発レビューの中-2）。
 *
 * @param <T> 一覧の行の型
 */
public sealed interface AdministrationListing<T> {

    /**
     * 権限があれば行を返し、なければ {@code denied} の例外を投げる。
     *
     * @param denied 権限がないときの例外（画面の層は 403 を渡す）
     */
    default List<T> itemsOr(Supplier<? extends RuntimeException> denied) {
        return switch (this) {
            case Permitted<T>(List<T> items) -> items;
            case NotPermitted<T> _ -> throw denied.get();
        };
    }

    /**
     * 照会できた行（リポジトリの順）。
     *
     * @param items 行
     */
    record Permitted<T>(List<T> items) implements AdministrationListing<T> {

        public Permitted {
            items = List.copyOf(items);
        }
    }

    /** システム管理者でない操作者（権限なし）。 */
    record NotPermitted<T>() implements AdministrationListing<T> {}
}
