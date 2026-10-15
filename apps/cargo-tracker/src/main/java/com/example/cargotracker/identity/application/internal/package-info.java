/**
 * アプリケーションサービス。アクセス・監査コンテキストの入力ポートの実装とコマンド。インターフェース層とインフラストラクチャ層の設定から使う。認証の入口（{@code infrastructure.security}）からは、{@code commandservices} と {@code session}（ふるまいを持たない session の属性の名前と URL。Bolt 33c）だけを使う（D-5 の例外。ADR-012 の決定 6）。
 */
package com.example.cargotracker.identity.application.internal;
