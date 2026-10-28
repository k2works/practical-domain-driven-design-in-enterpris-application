package com.example.cargotracker.shared.domain;

/**
 * 担当範囲の外の試行の記録のポート（Bolt 38f、ADR-019、IA-INV-08、US-16 AC4）。業務の各コンテキストが、業務担当者の担当企業の外の
 * 確定の操作を拒んだときに呼び、アクセス・監査（identity）が監査記録に同期で書く。共有カーネルに置く振る舞いのある型はこのポート 1 つ
 * だけの例外（業務のコンテキストが identity の公開 API を呼ぶと、identity → 見積りのイベントの依存と循環するため。ADR-019）。
 *
 * <p>約束:
 *
 * <ul>
 *   <li>呼ぶのは各コンテキストの画面の層の 1 つの部品だけで、業務のトランザクションの外で呼ぶ（外側のロールバックで記録が消えないように）。
 *       外側のトランザクションの中で呼ばれたら {@link IllegalStateException} にする
 *   <li>記録は自分のトランザクションで書く
 *   <li>確定の POST のときだけ呼び、GET では呼ばない（CSRF の守りがなく、他サイトから記録を書かせられるため。SEC-12）
 *   <li>書けなければ例外にする（画面は 500。404 で隠さない）
 * </ul>
 */
public interface OutOfScopeAttemptRecorder {

    /**
     * 担当範囲の外の試行を記録する。
     *
     * @param actor 操作者（業務担当者）
     * @param kind 対象の種類
     * @param resourceId 対象の ID（{@code target_id} の {@code VARCHAR(100)} に収まる形）
     */
    void recordOutOfScope(AuthenticatedActor actor, ScopedResourceKind kind, String resourceId);
}
