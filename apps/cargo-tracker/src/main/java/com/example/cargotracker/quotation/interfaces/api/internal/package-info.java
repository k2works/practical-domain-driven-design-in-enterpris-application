/**
 * 見積りの公開 API のインバウンドアダプター。公開 API のインターフェースを実装し、アプリケーションサービス（入力ポート）に委ねて、結果を公開 API
 * の型に変える（開発ガイドライン第 3 章のインターフェース層。2026-10-09 に {@code quotation.api} から移した）。公開 API の名前付き
 * インターフェース（{@code api}）には含めない。部品探索で拾う（画面のコントローラーと同じ）。
 */
package com.example.cargotracker.quotation.interfaces.api.internal;
