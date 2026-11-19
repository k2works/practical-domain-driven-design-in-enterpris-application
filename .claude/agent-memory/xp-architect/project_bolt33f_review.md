---
name: project-bolt33f-review
description: Bolt 33f 開発レビューの指摘。ArchUnit は String 定数の参照を見ない（インライン化）ので ADR-012 決定 3 の理由が事実と食い違う、A-03 の期間を全画面 Advice で描いた計画外の変更
metadata:
  type: project
---

Bolt 33f（2026-10-16、振る舞いを変えないリファクタリング）のアーキテクトレビューで指摘したこと。

- javac は `static final String` のコンパイル時定数をインライン化するので、ArchUnit は定数だけの参照を依存として見ない。実例: `identity.infrastructure.config.SecurityConfiguration` が `interfaces.web.PasswordResetController.URL` を参照しているが、`層の依存は内側に向かう` は緑。`SessionPaths` を `infrastructure.security` が参照しても依存の規則には現れない（`noMethods`・`fields` の見張りはパッケージ単位で効く）。
- ADR-012 決定 3 の「両方から参照できるのは application 層だけ／interfaces の規則に例外を足さずに済む」は、上の事実と前例に照らして言い過ぎ。
- A-03 の期間は計画（LoginController）と違い、全画面の `SessionTimerAdvice.sessionMaxLifetime` で描いた。計画と Javadoc（「属性があるときだけ置く」）に未反映。
- 現状の記述として残る古い文: ui_design の A-08 の URL（`SessionKeys` の URL）、architecture_backend 297 行目、ADR-012 決定 6 の「`SessionKeys` はふるまいを持たない」。

**Why:** 層の規則が定数を見ないことは、33c で足した例外（`application.internal.session`）の意味にも関わる。
**How to apply:** 定数の置き場所で層の規則を根拠にする議論が出たら、インライン化で規則が効かないことを先に確かめる。[[project-bolt33c-review]] の続き。
