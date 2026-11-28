---
name: feedback-prg-same-url-wait
description: cargo-tracker の画面の層のステップで、PRG で同じ URL に戻る操作は URL でなくリダイレクト後の GET の応答で待つ（axe が遷移中に動いて落ちる）
metadata:
  type: feedback
---

PRG で送る前と同じ画面（同じ URL）に戻る POST の後は、`waitForURL` が即座に返るので、リダイレクト後の GET の応答を `waitForResponse` で待ってから `checkAccessibility` を呼ぶ。

**Why:** Bolt 39gb の S-12 の採用のボタンで、URL で待ったために遷移の途中で axe が動き「Problematic axe-source, unable to inject」で uiTest が落ちた（17 分の流し直しになった）。

**How to apply:** 新しい画面の層のステップを書くとき、戻り先の URL が送る前と同じかを先に確かめる。uiTest は絞って流す口がないので、@demo を付けたシナリオは `demoVideo` で先に確かめると早い。
