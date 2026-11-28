---
name: project-bolt33c-review
description: Bolt 33c（SessionKeys・SessionPolicy・SessionUrlsAdvice・覚えた要求の名前の共有）のアーキテクトレビューの指摘と未解決の論点
metadata:
  type: project
---

Bolt 33c（2026-10-15）のレビューで指摘したこと。

- 規則の例外 `application.internal.session` の根拠は「ふるまいを持たない」だが、それを見張る ArchUnit がない（例外が黙って広がる）。
- 計画の「設計の約束」とステップ 3 に `@Bean RequestCache` が、設計図に消した `SessionLifetime` が残っていた。`application/internal/package-info.java` の直しが約束どおりされていない。`LayerArchitectureTest` の規則名と Javadoc が「コマンドサービスだけ」のまま。
- 期限の境界の判定 `!now.isBefore(start.plus(d))` が 4 か所に写されている。`SessionPolicy` は期間だけで判定を持たない。
- テンプレートに「15 分」の直書きが残る（印の知らせ、A-07・A-08）。A-01 だけ LockPolicy から描く形になった。33d の範囲にもない。

**Why:** 振る舞いの変わらない直しで、置き場所の例外を人が決めた Bolt。例外の条件を機械で守らないと、後の Bolt で崩れる。
**How to apply:** 33d 以降のレビューで、session パッケージの見張り、境界の判定の集約、期間の文言の直書きが片付いたか確かめる。[[project-bolt33a-review]]
