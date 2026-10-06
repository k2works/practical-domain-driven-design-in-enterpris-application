---
name: project-bolt15-heroku-demo-review
description: Bolt 15（Heroku デモ環境、dev プロファイル公開）運用レビューで残した指摘。配備のガードの穴、イメージの自動検証なし、ロールバック手順なし、ADR のタスク名ずれ（2026-10-06）
metadata:
  type: project
---

Bolt 15 の運用レビュー（2026-10-06）で指摘した未解消点。次の Bolt や W10（AWS）のレビューで解消を確かめる。

- 配備のガード（ops/scripts/deploy_demo.js の requireDeployableCommit）は build だけにあり、push・release を単独で実行すると素通り。ブランチが develop か、HEAD が push 済みかを見ない（feature ブランチの PR の緑で通る）。gitignore された src 配下のファイルは git status に出ずに build context に入る。イメージにコミットの SHA のラベルがない
- イメージの検証は手作業だけ（runtime に H2 がない、PORT、X-Forwarded-Proto で https のまま）。AT-06 は Gradle の構成を見るだけで、イメージは見ない。H1 は Spring Boot の DYNO による検出に依存し、版上げで黙って壊れうる
- 手順書にロールバックの手順がない
- ADR-013 と Bolt 15 計画の DoD・デモ項目が旧名 heroku:* のまま（実体は deploy:demo:*）
- dev の cookie Secure=false が公開の URL で動くことは ADR の帰結に書かれていない（計画の「AI の仮定」にだけある）

**Why:** アクセス制限なしは人の決定済み。残るのは「未検証のコードが出る」「何が出ているか分からない」「戻せない」の運用の穴。
**How to apply:** W10 の ECR・ECS で同じ Dockerfile を使うとき、runtime ステージの自動検証と SHA のラベルを最初に求める。[[project-bolt2-quality-gate-review]] の「ゲートが空振りする」系と同じ型の指摘。
