# TS-01 TOTP と Spring Security 7 の多要素認証のスパイク

[Bolt 13 計画](../../docs/development/cargo-tracker/bolt_13_plan.md) のスパイク。US-18（本人として認証される）の実装の前に、TOTP の生成・検証のライブラリと、Spring Security 7 の多要素認証で password の後に TOTP を求める 2 段階のログインの組み方を、学習テストで確かめる。結論は ADR 011 の案に書く。

- 本体（`apps/cargo-tracker`）の build・CI・SonarQube には含めない。ここで試すライブラリを本体に入れるのは、ADR 011 の承認の後（US-18 の Bolt）。
- US-18 の実装が終わったら、このディレクトリを消す。

## 動かし方

```bash
cd spikes/ts-01-totp
LC_ALL=C.UTF-8 ../../apps/cargo-tracker/gradlew test
```
