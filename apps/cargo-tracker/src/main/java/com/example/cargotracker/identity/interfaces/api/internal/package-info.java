/**
 * アクセス・監査のインバウンドアダプター。共有カーネルの担当範囲の外の試行の記録のポート（{@code OutOfScopeAttemptRecorder}。ADR-019。
 * Bolt 38f）を実装し、アプリケーションサービスに委ねる（開発ガイドライン第 3 章のインターフェース層）。アクセス・監査は自分の公開 API の型を
 * 持たず、このパッケージは名前付きインターフェースに含めない。部品探索で拾う（画面のコントローラーと同じ。組み立ての infrastructure.config
 * は interfaces を参照しない。前例は予約の {@code interfaces.api.internal}）。
 */
package com.example.cargotracker.identity.interfaces.api.internal;
