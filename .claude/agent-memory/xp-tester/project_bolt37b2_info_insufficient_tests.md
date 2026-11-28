---
name: project-bolt37b2-info-insufficient-tests
description: Bolt 37b2（US-06 AC4 情報不足）のテストレビューの指摘。info_insufficient を「除外」に置き換える変異が生存、hours() の分の枝、lambda の中の new、許容時間 24h の直書き
metadata:
  type: project
---

Bolt 37b2 のテストレビュー（2026-10-22）で見つけた未解消点。

- `ConstraintEvaluation.infoInsufficient()` を `!conforming()` に変える変異が生き残る。RoutingCaseTest:218-219 も保存の往復の統合テストも「適合 false・情報不足 true」の 2 件しかなく、期限超過だけの除外の候補で false を確かめていない
- `RoutingCaseViews.hours()`（:278）の分のある枝が未検証。秒は表示で落ちる
- `RoutingCaseConfirmationTest:253-254` の assertThatThrownBy の lambda の中で `new InfoFreshnessPolicy` を作る（T-139）
- 許容時間 `Duration.ofHours(24)` を DevSampleDataSmokeTest:351・AcceptanceTestConfiguration:189 に直書き（設定の既定値の写し）
- `InfoFreshnessSettingIntegrationTest` の @Nested + @TestPropertySource は Spring Framework 7（Boot 4.1）の SpringExtension がメソッド単位の ExtensionContext を使う前提で、外側のインスタンスのフィールドも入れ子のコンテキストから入る。dev の H2 はコンテキスト間で同じ mem DB を共有する（DB_CLOSE_DELAY=-1）

**Why:** 次の Bolt（37c の確定のときの鮮度）で同じ部品を使うので、残った穴が引き継がれる。
**How to apply:** 37c のレビューで、除外の区分ごとの info_insufficient、設定の写し、@Nested の前提を確かめる。[[project-bolt37b-review]]
