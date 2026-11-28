// リリースタスクのテスト（版・CHANGELOG・コミットとタグ・品質ゲート・登録）。実行: node --test ops/scripts/release.test.js
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import {
  CHANGELOG_PATH,
  VERSION_PATH,
  branchProblem,
  bumpVersion,
  executeRelease,
  generateChangelog,
  gradleGate,
  parseVersion,
  prependChangelog,
  replaceVersion,
} from './release.js';

const BUILD_GRADLE = "plugins {\n\tid 'org.springframework.boot' version '4.1.3'\n}\n\ngroup = 'com.example'\nversion = '0.0.1-SNAPSHOT'\n";

function git(dir, ...args) {
  return execFileSync('git', args, { cwd: dir, encoding: 'utf8', stdio: 'pipe' }).trim();
}

function write(dir, file, text) {
  fs.mkdirSync(path.dirname(path.join(dir, file)), { recursive: true });
  fs.writeFileSync(path.join(dir, file), text);
}

function commit(dir, file, subject) {
  write(dir, file, `${subject}\n`);
  git(dir, 'add', '--', file);
  git(dir, 'commit', '-q', '-m', subject);
}

/** build.gradle のある一時の Git リポジトリ。利用者の Git の設定（署名・フック）は使わない */
function repository(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'release-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  git(dir, 'init', '-q', '-b', 'develop');
  for (const [key, value] of [
    ['user.name', 'tester'],
    ['user.email', 'tester@example.com'],
    ['commit.gpgsign', 'false'],
    ['tag.gpgsign', 'false'],
    ['core.autocrlf', 'false'],
    ['core.hooksPath', 'no-hooks'],
  ]) {
    git(dir, 'config', key, value);
  }
  write(dir, VERSION_PATH, BUILD_GRADLE);
  git(dir, 'add', '--', VERSION_PATH);
  git(dir, 'commit', '-q', '-m', 'chore: 骨格');
  return dir;
}

test('parseVersion は build.gradle のアプリの版を読む（プラグインの版は読まない）', () => {
  assert.equal(parseVersion(BUILD_GRADLE), '0.0.1-SNAPSHOT');
  assert.equal(parseVersion(BUILD_GRADLE.replace('0.0.1-SNAPSHOT', '1.0.0').replaceAll('\n', '\r\n')), '1.0.0');
});

test('parseVersion は版の行がないか 2 つあると失敗する', () => {
  assert.throws(() => parseVersion("group = 'com.example'\n"), /version/);
  assert.throws(() => parseVersion(`${BUILD_GRADLE}version = '2.0.0'\n`), /version/);
});

test('replaceVersion はアプリの版の行だけを書き換え、改行を変えない', () => {
  const crlf = BUILD_GRADLE.replaceAll('\n', '\r\n');
  assert.equal(replaceVersion(crlf, '0.1.0'), crlf.replace('0.0.1-SNAPSHOT', '0.1.0'));
});

test('bumpVersion は数字の部分から計算し、-SNAPSHOT を付けない', () => {
  assert.equal(bumpVersion('0.0.1-SNAPSHOT', 'patch'), '0.0.2');
  assert.equal(bumpVersion('0.0.1-SNAPSHOT', 'minor'), '0.1.0');
  assert.equal(bumpVersion('0.0.1-SNAPSHOT', 'major'), '1.0.0');
  assert.equal(bumpVersion('1.0.0', 'minor'), '1.1.0');
  assert.throws(() => bumpVersion('1.0', 'patch'), /版/);
  assert.throws(() => bumpVersion('1.0.0', 'huge'), /huge/);
});

test('CHANGELOG は cargo-tracker のパスの feat・fix・perf・build と ! 付きだけを載せる', (t) => {
  const dir = repository(t);
  commit(dir, 'apps/cargo-tracker/a', 'feat(booking): 予約を足す');
  commit(dir, 'ops/terraform/b', 'fix(ops): 構成を直す');
  commit(dir, 'ops/scripts/operation.js', 'perf: 速くする');
  commit(dir, '.github/workflows/cargo-tracker-ci.yml', 'build: 版を上げる');
  commit(dir, 'apps/cargo-tracker/c', 'refactor(routing)!: 公開 API を変える');
  commit(dir, 'apps/cargo-tracker/d', 'test: テストを足す');
  commit(dir, 'apps/cargo-tracker/e', 'docs: 説明');
  commit(dir, 'apps/cargo-tracker/f', '規約でない件名');
  commit(dir, 'docs/g', 'feat: 文書の側の変更');
  commit(dir, 'ops/scripts/mkdocs.js', 'feat: ほかの運用スクリプト');

  const entry = generateChangelog(dir, '0.1.0', '2026-11-28');

  assert.match(entry, /^## \[0\.1\.0\] - 2026-11-28\n/);
  assert.match(entry, /### Breaking Changes\n\n- 公開 API を変える \([0-9a-f]+\)/);
  assert.match(entry, /### Features\n\n- 予約を足す \([0-9a-f]+\)\n\n/);
  assert.match(entry, /### Bug Fixes\n\n- 構成を直す /);
  assert.match(entry, /### Performance\n\n- 速くする /);
  assert.match(entry, /### Build\n\n- 版を上げる /);
  for (const absent of ['テストを足す', '説明', '規約でない件名', '文書の側の変更', 'ほかの運用スクリプト']) {
    assert.ok(!entry.includes(absent), absent);
  }
});

test('CHANGELOG の範囲は直前の cargo-tracker-v のタグからで、ほかのタグは無視する', (t) => {
  const dir = repository(t);
  commit(dir, 'apps/cargo-tracker/a', 'feat: タグの前');
  git(dir, 'tag', '-a', 'cargo-tracker-v0.1.0', '-m', 'cargo-tracker-v0.1.0');
  commit(dir, 'apps/cargo-tracker/b', 'feat: タグの後 1');
  git(dir, 'tag', '-a', 'v9.9.9', '-m', 'ほかのタグ');
  commit(dir, 'apps/cargo-tracker/c', 'feat: タグの後 2');

  const entry = generateChangelog(dir, '0.2.0', '2026-11-28');

  assert.ok(entry.includes('タグの後 1') && entry.includes('タグの後 2'));
  assert.ok(!entry.includes('タグの前'));
});

test('CHANGELOG は対象の変更がないとその旨を書く', (t) => {
  const dir = repository(t);

  assert.match(generateChangelog(dir, '0.0.2', '2026-11-28'), /対象の変更はありません/);
});

test('git の失敗は「変更なし」にせず、そのまま失敗する', (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'release-test-norepo-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  write(dir, '.git', 'gitdir: no-such-directory\n');

  assert.throws(() => generateChangelog(dir, '0.1.0', '2026-11-28'));
});

test('prependChangelog は見出しを 1 つに保ち、新しい版を上に足す', () => {
  const first = prependChangelog(null, '## [0.1.0] - 2026-11-28\n\n- a\n');
  const second = prependChangelog(first, '## [0.2.0] - 2026-12-01\n\n- b\n');

  assert.equal(first, '# Changelog\n\n## [0.1.0] - 2026-11-28\n\n- a\n');
  assert.equal(second, '# Changelog\n\n## [0.2.0] - 2026-12-01\n\n- b\n\n## [0.1.0] - 2026-11-28\n\n- a\n');
});

test('executeRelease は版と CHANGELOG だけをコミットしてタグを打つ', (t) => {
  const dir = repository(t);
  commit(dir, 'apps/cargo-tracker/a', 'feat: 予約を足す');
  write(dir, 'unrelated.txt', 'ステージ済みの別の変更\n');
  git(dir, 'add', '--', 'unrelated.txt');

  const result = executeRelease(dir, 'minor', '2026-11-28');

  assert.deepEqual(result, { oldVersion: '0.0.1-SNAPSHOT', newVersion: '0.1.0', tag: 'cargo-tracker-v0.1.0' });
  assert.equal(git(dir, 'log', '-1', '--format=%s'), 'chore(release): cargo-tracker v0.1.0');
  assert.deepEqual(git(dir, 'show', '--name-only', '--format=', 'HEAD').split('\n').sort(), [CHANGELOG_PATH, VERSION_PATH].sort());
  assert.equal(git(dir, 'cat-file', '-t', 'cargo-tracker-v0.1.0'), 'tag');
  assert.equal(git(dir, 'rev-parse', 'cargo-tracker-v0.1.0^{commit}'), git(dir, 'rev-parse', 'HEAD'));
  assert.equal(parseVersion(fs.readFileSync(path.join(dir, VERSION_PATH), 'utf8')), '0.1.0');
  assert.match(fs.readFileSync(path.join(dir, CHANGELOG_PATH), 'utf8'), /予約を足す/);
  assert.equal(git(dir, 'status', '--porcelain'), 'A  unrelated.txt');
});

test('executeRelease は同じタグがあると、何も書き換える前に失敗する', (t) => {
  const dir = repository(t);
  git(dir, 'tag', '-a', 'cargo-tracker-v0.1.0', '-m', '既にある');
  const head = git(dir, 'rev-parse', 'HEAD');

  assert.throws(() => executeRelease(dir, 'minor', '2026-11-28'), /cargo-tracker-v0\.1\.0/);

  assert.equal(git(dir, 'rev-parse', 'HEAD'), head);
  assert.equal(git(dir, 'status', '--porcelain'), '');
});

test('executeRelease は版か CHANGELOG に未コミットの変更があると、何もせず失敗する', (t) => {
  const dir = repository(t);
  write(dir, VERSION_PATH, `${BUILD_GRADLE}// 作業中\n`);
  const head = git(dir, 'rev-parse', 'HEAD');

  assert.throws(() => executeRelease(dir, 'minor', '2026-11-28'), /未コミット/);

  assert.equal(git(dir, 'rev-parse', 'HEAD'), head);
  assert.equal(git(dir, 'tag', '--list'), '');
});

test('branchProblem は develop で origin と一致するときだけ通す', () => {
  assert.equal(branchProblem({ branch: 'develop', ahead: 0, behind: 0 }), null);
  assert.match(branchProblem({ branch: 'main', ahead: 0, behind: 0 }), /develop/);
  assert.match(branchProblem({ branch: 'develop', ahead: 1, behind: 0 }), /origin\/develop/);
  assert.match(branchProblem({ branch: 'develop', ahead: 0, behind: 2 }), /origin\/develop/);
});

test('gradleGate は cargo-tracker で全部のゲートと bootJar を流す', () => {
  const root = path.resolve('repo');
  const tasks = 'check documentationTest uiTest bootJar';

  assert.deepEqual(gradleGate(root, 'win32'), { command: `.\\gradlew.bat ${tasks}`, cwd: path.join(root, 'apps', 'cargo-tracker') });
  assert.deepEqual(gradleGate(root, 'linux'), { command: `./gradlew ${tasks}`, cwd: path.join(root, 'apps', 'cargo-tracker') });
});

test('gulpfile は release のタスクを登録し、ほかのタスクも残る', async () => {
  const { default: gulp } = await import('../../gulpfile.js');
  const names = gulp.tree().nodes;

  for (const name of ['release:dry-run', 'release:preflight', 'release:patch', 'release:minor', 'release:major', 'deploy:rollback', 'okf:check']) {
    assert.ok(names.includes(name), name);
  }
  assert.deepEqual(names.filter((name) => name.startsWith('release:deploy')), []);
  // 書き換える部分は、品質ゲートを通らずに呼べる名前のタスクにしない
  assert.deepEqual(names.filter((name) => name.endsWith(':execute')), []);
});
