/**
 * Web の表示の部品。コンテキストの画面（{@code interfaces.web}）が共通に使う日時表示・期間表示を置く（UI 設計の共通部品
 * 「日時表示」。Bolt 22、#41）。業務の型と規則は置かない。コンテキストから参照してよいのは、platform のうちこの名前付き
 * インターフェースだけ（バックエンドアーキテクチャ）。
 */
@org.springframework.modulith.NamedInterface("web")
package com.example.cargotracker.platform.web;
