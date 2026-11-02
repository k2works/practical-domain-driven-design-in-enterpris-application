# orchestrating-development — 本プロジェクトの決まり

SKILL.md とあわせて読む。SKILL.md と食い違うときは本ファイルを正とする。

## サブエージェント・フォーク・Codex に作業を任せるとき

### 複数行のスクリプトを heredoc で書かない（T-204・T-212）

- 複数行のスクリプト（Python・シェル・置換の処理）とコミットメッセージは、heredoc で渡さない。Write でファイルにしてから、そのファイルを実行する（コミットは `git commit -F <ファイル>`）
- heredoc の中に別の heredoc や、引用符の多い Python を入れない
- 標準入力を待つコマンドを使わない。標準入力が要るときは、標準入力を /dev/null につなぐ
- サブエージェント・フォーク・Codex に任せるときは、指示の **最初に** この決まりを書く

**なぜ**: heredoc と標準入力の待ちで、任せた作業が止まり、作業ツリーに途中の状態が残った。Try に置いても繰り返した（Bolt 38b で起き、38b2b・38b3b・38c2・38d・38e・38f・38f2・38g の 8 本で再発した）。W6 の締め（2026-11-02、human:kakimomokuri）で、Try からこの決まりに上げた。

**根拠**: [Bolt 38b 終了報告](../../../docs/development/cargo-tracker/bolt_38b_report.md) の T-204、[Bolt 38b3b 終了報告](../../../docs/development/cargo-tracker/bolt_38b3b_report.md) の T-212、[リリース計画](../../../docs/development/cargo-tracker/release_plan.md) の「W6 の締めの見直し」
