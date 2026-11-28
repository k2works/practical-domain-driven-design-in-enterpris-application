---
name: project-bolt32d-review
description: Bolt 32d レビュー（identity の合成ルートの規則 AT-07、テストのコンテキスト共有）で残した指摘
metadata:
  type: project
---

Bolt 32d（2026-10-14）のアーキテクトレビューで指摘したこと。

- AT-07 は `@Bean` の戻り値の型しか見ないため、`@Bean` 本体での `new` による送信アダプターの組み立て（例: `DevLoginGuardConfiguration` が `new DevTotpCodes(...)::codeFor` を返す）や `org.springframework..` の `FactoryBean<X>` は素通りする。「送信アダプターは部品探索で拾う」の正の側（crypto/persistence の実装に `@Component`/`@Repository`）も見張っていない。
- テストのコンテキスト共有は規約頼み。spy の基底はあるが、時計だけの 4 クラスは 3 行の注釈を手で写している。合成注釈（例 `@IdentityIntegrationTest`）や、基底の外の `@MockitoSpyBean` を拒む見張りを提案した。
- `MutableClock` は共有コンテキストで状態が残る。時刻の初期化を基底の `@BeforeEach` に寄せる提案。

**Why:** 規則と見張りの抜け道は次の Bolt（Bolt 34 で合成ルートに部品が増える）で効いてくる。
**How to apply:** Bolt 34 以降の identity の合成ルート・統合テストのレビューで、これらが解消されたかを確かめる。[[project-bolt32c-review]]
