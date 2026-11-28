'use strict';

/**
 * cargo-tracker のリリースタスク（Bolt 71）。
 *
 * - 版の正本は apps/cargo-tracker/build.gradle の version。版は数字の部分から計算し、-SNAPSHOT を付けずに書き戻す
 * - タグは cargo-tracker-vX.Y.Z。CHANGELOG は直前のこのタグからの、cargo-tracker のパスの feat・fix・perf・build と ! 付きのコミット
 * - 配備はしない（配備は cargo-tracker-deploy ワークフロー）。push もしない
 */

import fs from 'fs';
import path from 'path';
import { execFileSync, execSync } from 'child_process';

const APP_DIR = 'apps/cargo-tracker';
const TAG_PREFIX = 'cargo-tracker-v';
const RELEASE_BRANCH = 'develop';
const GRADLE_TASKS = 'check documentationTest uiTest bootJar';

export const VERSION_PATH = `${APP_DIR}/build.gradle`;
export const CHANGELOG_PATH = `${APP_DIR}/CHANGELOG.md`;

const CHANGELOG_SOURCES = [APP_DIR, 'ops/terraform', ':(glob)ops/scripts/operation*.js', ':(glob).github/workflows/cargo-tracker-*.yml'];
const BREAKING = 'Breaking Changes';
const SECTIONS = new Map([
  ['feat', 'Features'],
  ['fix', 'Bug Fixes'],
  ['perf', 'Performance'],
  ['build', 'Build'],
]);

// プラグインの `id '…' version '…'` と区別するため、行の先頭の version だけを読む
const VERSION_LINE = /^(version\s*=\s*)(['"])([^'"\r\n]+)\2/gm;

function git(rootDir, ...args) {
  // 失敗は握りつぶさない。履歴の全体を読むので、出力の上限を広げる
  return execFileSync('git', args, { cwd: rootDir, encoding: 'utf8', stdio: 'pipe', maxBuffer: 64 * 1024 * 1024 }).trim();
}

// ============================================
// 版
// ============================================

export function parseVersion(buildGradle) {
  const found = [...buildGradle.matchAll(VERSION_LINE)];
  if (found.length !== 1) {
    throw new Error(`${VERSION_PATH} の version の行が ${found.length} 個あります（1 個のはずです）`);
  }
  return found[0][3];
}

export function replaceVersion(buildGradle, version) {
  parseVersion(buildGradle);
  return buildGradle.replace(VERSION_LINE, `$1$2${version}$2`);
}

export function bumpVersion(version, type) {
  const found = /^(\d+)\.(\d+)\.(\d+)(-SNAPSHOT)?$/.exec(version);
  if (!found) {
    throw new Error(`版の形式が X.Y.Z か X.Y.Z-SNAPSHOT ではありません: ${version}`);
  }
  const [major, minor, patch] = found.slice(1, 4).map(Number);
  const next = {
    major: `${major + 1}.0.0`,
    minor: `${major}.${minor + 1}.0`,
    patch: `${major}.${minor}.${patch + 1}`,
  }[type];
  if (!next) {
    throw new Error(`版の上げ方が分かりません: ${type}`);
  }
  return next;
}

function currentVersion(rootDir) {
  return parseVersion(fs.readFileSync(path.join(rootDir, VERSION_PATH), 'utf8'));
}

// ============================================
// CHANGELOG
// ============================================

function releaseCommits(rootDir) {
  const lastTag = git(rootDir, 'tag', '--list', `${TAG_PREFIX}*`, '--merged', 'HEAD', '--sort=-v:refname').split('\n')[0];
  const range = lastTag ? `${lastTag}..HEAD` : 'HEAD';
  const log = git(rootDir, 'log', range, '--pretty=format:%h%x1f%s', '--', ...CHANGELOG_SOURCES);
  return log ? log.split('\n').map((line) => line.split('\x1f')) : [];
}

export function generateChangelog(rootDir, version, date) {
  const grouped = new Map([BREAKING, ...SECTIONS.values()].map((title) => [title, []]));
  for (const [hash, subject] of releaseCommits(rootDir)) {
    const found = /^(\w+)(?:\([^)]*\))?(!)?:\s*(.+)$/.exec(subject);
    if (!found) continue;
    const [, type, breaking, message] = found;
    const title = breaking ? BREAKING : SECTIONS.get(type);
    if (title) grouped.get(title).push(`- ${message} (${hash})`);
  }

  const sections = [...grouped]
    .filter(([, lines]) => lines.length > 0)
    .map(([title, lines]) => `### ${title}\n\n${lines.join('\n')}\n`);
  const body = sections.length > 0 ? sections.join('\n') : '- 対象の変更はありません\n';
  return `## [${version}] - ${date}\n\n${body}`;
}

export function prependChangelog(existing, entry) {
  const previous = (existing ?? '').replace(/^# Changelog\s*/, '');
  return `# Changelog\n\n${entry}${previous ? `\n${previous}` : ''}`;
}

// ============================================
// リリースの実行
// ============================================

export function executeRelease(rootDir, type, date) {
  const oldVersion = currentVersion(rootDir);
  const newVersion = bumpVersion(oldVersion, type);
  const tag = `${TAG_PREFIX}${newVersion}`;

  // 何かを書き換える前に、失敗する条件を確かめる
  if (git(rootDir, 'tag', '--list', tag)) {
    throw new Error(`タグ ${tag} はもうあります`);
  }
  if (git(rootDir, 'status', '--porcelain', '--', VERSION_PATH, CHANGELOG_PATH)) {
    throw new Error(`${VERSION_PATH} か ${CHANGELOG_PATH} に未コミットの変更があります`);
  }

  const entry = generateChangelog(rootDir, newVersion, date);
  const versionFile = path.join(rootDir, VERSION_PATH);
  const changelogFile = path.join(rootDir, CHANGELOG_PATH);
  const changelog = fs.existsSync(changelogFile) ? fs.readFileSync(changelogFile, 'utf8') : null;
  fs.writeFileSync(versionFile, replaceVersion(fs.readFileSync(versionFile, 'utf8'), newVersion));
  fs.writeFileSync(changelogFile, prependChangelog(changelog, entry));

  const message = `chore(release): cargo-tracker v${newVersion}`;
  git(rootDir, 'add', '--', VERSION_PATH, CHANGELOG_PATH);
  // パスを指定して、ほかにステージされた変更を混ぜない
  git(rootDir, 'commit', '-m', message, '--', VERSION_PATH, CHANGELOG_PATH);
  git(rootDir, 'tag', '-a', tag, '-m', tag);

  return { oldVersion, newVersion, tag };
}

// ============================================
// 品質ゲート
// ============================================

export function branchProblem({ branch, ahead, behind }) {
  if (branch !== RELEASE_BRANCH) {
    return `リリースは ${RELEASE_BRANCH} で行います（今は ${branch}）`;
  }
  if (ahead !== 0 || behind !== 0) {
    return `origin/${RELEASE_BRANCH} と一致していません（ahead ${ahead}、behind ${behind}）`;
  }
  return null;
}

export function gradleGate(rootDir, platform) {
  // Windows の cmd.exe は ./gradlew を実行できないため gradlew.bat を使う
  const wrapper = platform === 'win32' ? '.\\gradlew.bat' : './gradlew';
  return { command: `${wrapper} ${GRADLE_TASKS}`, cwd: path.join(rootDir, APP_DIR) };
}

function checkClean(rootDir) {
  const status = git(rootDir, 'status', '--porcelain');
  if (status) {
    throw new Error(`作業ツリーが clean ではありません。コミットか退避をしてから実行してください。\n${status}`);
  }
}

function checkBranch(rootDir) {
  git(rootDir, 'fetch', '--quiet', 'origin', RELEASE_BRANCH);
  const [behind, ahead] = git(rootDir, 'rev-list', '--left-right', '--count', `origin/${RELEASE_BRANCH}...HEAD`)
    .split(/\s+/)
    .map(Number);
  const problem = branchProblem({ branch: git(rootDir, 'rev-parse', '--abbrev-ref', 'HEAD'), ahead, behind });
  if (problem) throw new Error(problem);
}

function runOpsTests(rootDir) {
  const files = fs
    .readdirSync(path.join(rootDir, 'ops/scripts'))
    .filter((name) => name.endsWith('.test.js'))
    .map((name) => `ops/scripts/${name}`);
  execFileSync(process.execPath, ['--test', ...files], { cwd: rootDir, stdio: 'inherit' });
}

function runGradleGate(rootDir) {
  const { command, cwd } = gradleGate(rootDir, process.platform);
  execSync(command, { cwd, stdio: 'inherit' });
}

// ============================================
// タスク登録
// ============================================

function today() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * リリースタスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 * @param {{ rootDir?: string }} [options] - オプション
 * @param {string} [options.rootDir] - リポジトリのルート（省略時は実行したディレクトリ）
 */
export default function (gulp, options = {}) {
  const rootDir = options.rootDir || path.resolve(process.cwd());
  const step = (run) => (done) => {
    try {
      run();
      done();
    } catch (error) {
      done(error);
    }
  };
  const task = (name, run) => gulp.task(name, step(run));

  task('release:preflight:clean', () => checkClean(rootDir));
  task('release:preflight:branch', () => checkBranch(rootDir));
  task('release:preflight:ops-test', () => runOpsTests(rootDir));
  task('release:preflight:gradle', () => runGradleGate(rootDir));
  gulp.task(
    'release:preflight',
    gulp.series(
      'release:preflight:clean',
      'release:preflight:branch',
      'release:preflight:ops-test',
      'release:preflight:gradle',
      // ゲートは約 30 分かかる。その間に作業ツリーや HEAD が変わっていないことを、もう一度確かめる
      'release:preflight:clean',
      'release:preflight:branch'
    )
  );

  for (const type of ['patch', 'minor', 'major']) {
    // 書き換える部分は名前を付けて登録しない（品質ゲートを通らずに呼べないようにする）
    const execute = step(() => {
      const { oldVersion, newVersion, tag } = executeRelease(rootDir, type, today());
      console.log(`版: ${oldVersion} -> ${newVersion}、タグ: ${tag}`);
      console.log(`内容を確かめてから push します: git push origin ${RELEASE_BRANCH} ${tag}`);
    });
    execute.displayName = `${type} の版・CHANGELOG・タグ`;
    gulp.task(`release:${type}`, gulp.series('release:preflight', execute));
  }

  task('release:dry-run', () => {
    const version = currentVersion(rootDir);
    console.log('=== Release Dry Run（何も変更しません） ===\n');
    console.log(`今の版: ${version}`);
    for (const type of ['patch', 'minor', 'major']) {
      const next = bumpVersion(version, type);
      console.log(`  ${type}: ${next}（タグ ${TAG_PREFIX}${next}）`);
    }
    console.log(`\n--- ${CHANGELOG_PATH} に足す内容 ---\n`);
    console.log(generateChangelog(rootDir, '次の版', today()));
  });
}
