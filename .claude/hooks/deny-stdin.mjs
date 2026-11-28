// PreToolUse フック（Bash）: 標準入力からプログラムを読む形のインタープリタの起動を拒む（W11 計画の CP-5）。
// 拒むのは「python -」「node -」と、引数も入力の向け先もない python・node（対話モードになる）。
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

const INTERPRETER = String.raw`(?:^|[;&|(\n])[ \t]*(?:[A-Za-z_]\w*=\S*[ \t]+)*(?:\S*/)?(?:python3?|py|node)(?:\.exe)?`;
// 引数の「-」（標準入力からプログラムを読む）。前に -I などの 1 文字のオプションがあってもよい
const DASH = new RegExp(String.raw`${INTERPRETER}(?:[ \t]+-[A-Za-z]+)*[ \t]+-(?=[ \t]|$|[<;|&)\n])`);
// 引数がない起動。入力を /dev/null に向けていれば固まらないので通す
const BARE = new RegExp(String.raw`${INTERPRETER}[ \t]*(?=$|[;|&)\n]|<(?![ \t]*/dev/null))`);

/** here document の本文と引用符の中身を除く（コミットの本文などの文字列に反応しないようにする） */
function stripText(command) {
  return command
    .replace(/<<-?[ \t]*(['"]?)([A-Za-z_]\w*)\1([^\n]*)\n[\s\S]*?\n[ \t]*\2(?=\n|$)/g, '<<$2$3')
    .replace(/'[^']*'/g, "''")
    .replace(/"(?:\\.|[^"\\])*"/g, '""');
}

export function deniedReason(command) {
  const text = stripText(command);
  if (DASH.test(text) || BARE.test(text)) {
    return '標準入力からプログラムを読む形（python -、node -、引数のない起動）は使いません。スクリプトをファイルに書いて、そのパスを渡してください（W11 計画の CP-5）。';
  }
  return null;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const input = JSON.parse(fs.readFileSync(0, 'utf8'));
  const reason = deniedReason(input.tool_input?.command ?? '');
  if (reason) {
    // 終了コード 2 は呼び出しを止め、標準エラーの文をモデルに返す
    process.stderr.write(reason);
    process.exit(2);
  }
}
