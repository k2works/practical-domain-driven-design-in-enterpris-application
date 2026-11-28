import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { deniedReason } from './deny-stdin.mjs';

const DENIED = [
  'python -',
  "python - <<'EOF'\nprint(1)\nEOF",
  'cd repo && python3 - <<EOF\nprint(1)\nEOF',
  'python -I - < input.txt',
  'python - </dev/null',
  'cat a.py | python',
  'node -',
  'echo x; node',
  'FOO=1 python',
  'python <<EOF\nprint(1)\nEOF',
];
const ALLOWED = [
  'python script.py',
  'python -c "print(1)"',
  'python -m http.server',
  'node --test ops/scripts/release.test.js',
  'node -e "1"',
  'node script.mjs "$S/a" < /dev/null',
  'python < /dev/null',
  'git commit -m "python - の形は使わない"',
  "cat > note.sh <<'EOF'\npython -\nEOF",
  'grep -n "node -" file.md',
  'which python node',
  './gradlew test --offline > log 2>&1 < /dev/null',
];

test('標準入力からプログラムを読む形を拒む', () => {
  for (const command of DENIED) assert.ok(deniedReason(command), command);
});

test('ふだんの形は通す', () => {
  for (const command of ALLOWED) assert.equal(deniedReason(command), null, command);
});

test('フックとして呼ぶと、拒むときは終了コード 2 と理由、通すときは 0', () => {
  const script = fileURLToPath(new URL('./deny-stdin.mjs', import.meta.url));
  const run = (command) =>
    spawnSync(process.execPath, [script], { input: JSON.stringify({ tool_input: { command } }), encoding: 'utf8' });
  const denied = run('python -');
  assert.equal(denied.status, 2);
  assert.match(denied.stderr, /CP-5/);
  assert.equal(run('ls').status, 0);
});
