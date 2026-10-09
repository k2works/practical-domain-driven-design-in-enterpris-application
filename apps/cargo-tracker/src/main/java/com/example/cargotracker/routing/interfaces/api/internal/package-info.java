/**
 * 経路設計の公開 API のインバウンドアダプター。公開 API のインターフェースを実装し、アプリケーションサービス（照会）に委ねて、結果を公開 API
 * の型に変える（開発ガイドライン第 3 章のインターフェース層。Bolt 25）。公開 API の名前付きインターフェース（{@code api}）には含めない。
 * 部品探索で拾う（画面のコントローラーと同じ。組み立ての infrastructure.config は interfaces を参照しない）。
 */
package com.example.cargotracker.routing.interfaces.api.internal;
