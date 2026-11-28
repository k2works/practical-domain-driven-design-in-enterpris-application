---
name: project-bolt34c-review
description: Bolt 34c（監査の書き手の文書・AT-09〜11・SpotBugs のパッケージ除外・login_failure 注釈）の開発レビューで見つけた残りのリスク
metadata:
  type: project
---

Bolt 34c の開発レビュー（2026-10-17）で指摘したこと。

- IA-INV-03・04 の「session を捨てるのは利用停止・無効な企業・利用者なしと再開の消去」は網羅でない。password の再設定の完了（revokeAll）と TOTP の再登録（revokeOthers）も捨てる。開始準備の調べが SessionRevalidationService と 34d だけを見た
- 回復コードのログインで LOGIN_SUCCEEDED の追記が失敗したとき、session は password の段のまま残る見込み（例外はフィルターの失敗の扱いを通らない）。特性テストは 3xx しか見ず、別のコードで再試行でき、さらにコードを消費しうる点を固定していない
- AT-11 の 2 本目（ルートの @Configuration は ClockConfiguration だけ）は、広がりの見張りではなく例外の名前が古くならない見張り。広がりは 1 本目の完全名で止まっている
- SpotBugs の EI_EXPOSE_REP2 のパッケージ除外は、application.internal の結果の record（List.copyOf の防御の複製をしている）の将来の指摘も黙らせる
- ClockConfiguration の「置く config がない」の前提は、`platform` パッケージを候補にしていない

**Why:** 振る舞いを変えない Bolt でも、文書の列挙や見張りの名前が事実より強く言うと、次の Bolt の判断の前提を誤らせる。
**How to apply:** session・監査の列挙を見たら UserSessionRevoker の呼び出し元を grep して突き合わせる。T-135 の「広がらない見張り」は、どの変更で落ちるかを変異で言えるか確かめる。関連 [[project-bolt34d-review]]、[[project-bolt33a-review]]
