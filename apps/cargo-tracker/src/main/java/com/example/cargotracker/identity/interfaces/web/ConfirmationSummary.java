package com.example.cargotracker.identity.interfaces.web;

import java.util.List;

/**
 * 確認の画面のエラー要約（共通部品「エラー要約」。S-20 の登録の画面（Bolt 38b3b）と認証アプリの登録の解除の確認（Bolt 38c）が使う）。3 つ目の
 * 確認の画面を作るときに、登録の画面の部品から寄せた（Bolt 38e。38c の開発レビューの低の行）。詳細の画面の要約は
 * {@link UserDetailPage.ErrorSummary}。
 *
 * @param title 見出し
 * @param items 誤り（欄の順）
 */
public record ConfirmationSummary(String title, List<Item> items) {

    public ConfirmationSummary {
        items = List.copyOf(items);
    }

    /**
     * エラー要約の 1 件。
     *
     * @param href 欄へのリンク先（リンクしないなら null）
     * @param text 示す文（「表示名: 文言」）
     */
    public record Item(String href, String text) {}
}
