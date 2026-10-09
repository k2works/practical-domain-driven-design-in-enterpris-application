'use strict';

import fs from 'fs';
import path from 'path';
import { execFileSync } from 'child_process';

// ============================================
// 設定
// ============================================

/**
 * Bolt のデモ項目の動画（docs/assets/demo/<Bolt>/*.webm）を、受入の証跡として Issue のコメントに添付する。
 * gh CLI の --attach（v2.99.0 から。issue・pr の create・comment・edit。動画は .webm・.mp4・.mov で 1 件 100 MB まで、
 * 1 回 50 件まで。アップロードは取り消せない）を使う。動画はコメントの中で再生できる。
 * 手順は docs/operation/cargo-tracker/index.md の「Issue への受入動画の添付」を参照。
 */
const REPO = 'k2works/practical-domain-driven-design-in-enterpris-application';
const DEMO_DIR = path.join(process.cwd(), 'docs', 'assets', 'demo');
const FEATURE_DIR = path.join(process.cwd(), 'apps', 'cargo-tracker', 'src', 'test', 'resources', 'features');
/** --attach が入った gh の版 */
const MIN_GH_VERSION = [2, 99, 0];
const MAX_VIDEO_BYTES = 100 * 1024 * 1024;
const MAX_ATTACHMENTS = 50;
/**
 * これまでの Bolt の動画の添付先（各 Bolt の計画の Issue 欄のうち R0.1 の Issue）。新しい Bolt は終了報告の承認の後に足す。
 * 手順書（docs/operation/cargo-tracker/index.md）の表と同じにする。
 */
const BOLT_ISSUES = {
  'bolt-01': 1,
  'bolt-03': 1,
  'bolt-04': 2,
  'bolt-05': 3,
  'bolt-06': 3,
  'bolt-07': 2,
  'bolt-08': 2,
  'bolt-09': 37,
  'bolt-10': 4,
  'bolt-11': 4,
  'bolt-12': 5,
  'bolt-14': 6,
  'bolt-17': 7,
  'bolt-19': 8,
  'bolt-20': 5,
  'bolt-21': 9,
  'bolt-23b': 10,
  'bolt-24': 10,
  'bolt-25': 10,
  'bolt-25b': 10,
};

/** 添付したコメントの目印。同じ Bolt を同じ Issue に 2 回添付しない。 */
function marker(bolt) {
  return `<!-- issue-attach-demo:${bolt} -->`;
}

// ============================================
// ヘルパー関数
// ============================================

/**
 * 画面の層の受入シナリオから、デモ項目のタグ（@demo-<Bolt>/<名前>）ごとに、シナリオ名と受入条件のタグを集める。
 * @param {string} bolt - 例: bolt-19
 * @returns {Map<string, {scenario: string, criteria: string[], feature: string}>} 動画の名前 → シナリオ
 */
function demoScenarios(bolt) {
  const scenarios = new Map();
  const files = fs.readdirSync(FEATURE_DIR, { recursive: true }).filter((file) => file.endsWith('.feature'));
  for (const file of files) {
    const lines = fs.readFileSync(path.join(FEATURE_DIR, file), 'utf8').split('\n');
    lines.forEach((line, index) => {
      const tags = line.trim().split(/\s+/).filter((tag) => tag.startsWith('@'));
      const demo = tags.find((tag) => tag.startsWith(`@demo-${bolt}/`));
      if (!demo) {
        return;
      }
      const next = lines.slice(index + 1).find((candidate) => candidate.trim() !== '');
      const scenario = (next || '').replace(/^\s*(シナリオ|シナリオアウトライン|Scenario):\s*/, '').trim();
      scenarios.set(demo.split('/')[1], {
        scenario,
        criteria: tags.filter((tag) => /^@US-\d+(-AC\d+)?$/.test(tag)).map((tag) => tag.slice(1)),
        // コメントの本文は OS によらず / 区切りにする
        feature: file.split(path.sep).join('/'),
      });
    });
  }
  return scenarios;
}

/**
 * gh の版が --attach に対応するか（v2.99.0 以上）
 * @returns {string} gh の版
 */
function checkGhVersion() {
  let output;
  try {
    output = execFileSync('gh', ['--version'], { encoding: 'utf8' });
  } catch {
    throw new Error('gh CLI が見つかりません。v2.99.0 以上を入れてください');
  }
  const match = output.match(/gh version (\d+)\.(\d+)\.(\d+)/);
  if (!match) {
    throw new Error(`gh の版を読めません: ${output.split('\n')[0]}`);
  }
  const version = match.slice(1, 4).map(Number);
  for (let i = 0; i < 3; i++) {
    if (version[i] !== MIN_GH_VERSION[i]) {
      if (version[i] < MIN_GH_VERSION[i]) {
        throw new Error(`gh ${version.join('.')} は --attach に対応していません。v${MIN_GH_VERSION.join('.')} 以上に上げてください`);
      }
      break;
    }
  }
  return version.join('.');
}

/**
 * コメントの本文。動画ごとに見出し（シナリオ名と受入条件）と参照（![](<相対パス>)）を置く。
 * gh は本文の参照をアップロードした動画に書き換える（動画は再生できる形で表示され、代替テキストは持たない）。
 */
function commentBody(bolt, videos, scenarios) {
  const number = bolt.replace(/^bolt-0?/, '');
  const lines = [
    `## Bolt ${number} の受入動画（デモ項目）`,
    '',
    `画面の層の受入シナリオ（\`@demo-${bolt}/…\`）を \`./gradlew demoVideo\` で録画したもの。`,
    '',
  ];
  for (const video of videos) {
    const name = path.basename(video, path.extname(video));
    const found = scenarios.get(name);
    lines.push(`### ${found ? found.scenario : name}`);
    lines.push('');
    if (found && found.criteria.length > 0) {
      lines.push(`受入条件: ${found.criteria.join('、')}（\`${found.feature}\`）`);
      lines.push('');
    }
    lines.push(`![${name}](${video})`);
    lines.push('');
  }
  lines.push('---');
  lines.push('_`npx gulp issue:attach-demo` で添付_');
  lines.push(marker(bolt));
  return lines.join('\n');
}

// ============================================
// Gulp タスク
// ============================================

/**
 * Issue にその Bolt の目印のあるコメントがすでにあるか（REST。gh api）。
 */
function alreadyAttached(bolt, issue) {
  const bodies = execFileSync(
    'gh',
    ['api', '--paginate', `repos/${REPO}/issues/${issue}/comments`, '--jq', '.[].body'],
    { encoding: 'utf8' },
  );
  return bodies.includes(marker(bolt));
}

/**
 * 1 つの Bolt の動画を 1 つの Issue に添付する。
 * @returns {string} 結果（attached・skipped・dry-run）
 */
function attach(bolt, issue, dryRun) {
  // 分けた Bolt は末尾に英小文字を 1 つ付ける（例: bolt-23b。Bolt 23 から画面を分けた）
  if (!/^bolt-\d+[a-z]?$/.test(bolt || '')) {
    throw new Error('DEMO_BOLT に Bolt を bolt-19（分けた Bolt は bolt-23b）の形で指定してください');
  }
  if (!/^\d+$/.test(String(issue || ''))) {
    throw new Error('DEMO_ISSUE に Issue の番号を指定してください');
  }
  const dir = path.join(DEMO_DIR, bolt);
  if (!fs.existsSync(dir)) {
    throw new Error(`${path.relative(process.cwd(), dir)} がありません。先に ./gradlew demoVideo で録画してください`);
  }
  const videos = fs
    .readdirSync(dir)
    .filter((file) => /\.(webm|mp4|mov)$/.test(file))
    .sort()
    .map((file) => path.posix.join('docs', 'assets', 'demo', bolt, file));
  if (videos.length === 0) {
    throw new Error(`${bolt} の動画がありません`);
  }
  if (videos.length > MAX_ATTACHMENTS) {
    throw new Error(`添付は 1 回 ${MAX_ATTACHMENTS} 件までです（${videos.length} 件）`);
  }
  for (const video of videos) {
    const size = fs.statSync(video).size;
    if (size > MAX_VIDEO_BYTES) {
      throw new Error(`${video} は 100 MB を超えています（${size} バイト）`);
    }
  }
  const body = commentBody(bolt, videos, demoScenarios(bolt));
  if (dryRun) {
    console.log(body);
    console.log('');
    console.log(`$ gh issue comment ${issue} --repo ${REPO} --body <上の本文> ${videos.map((video) => `--attach ${video}`).join(' ')}`);
    console.log('');
    return 'dry-run';
  }
  if (alreadyAttached(bolt, issue)) {
    console.log(`${bolt} の動画は #${issue} に添付済みです（目印のコメントがあります）。飛ばします`);
    return 'skipped';
  }
  const args = ['issue', 'comment', String(issue), '--repo', REPO, '--body', body];
  for (const video of videos) {
    args.push('--attach', video);
  }
  console.log(`${bolt} の ${videos.length} 本の動画を #${issue} に添付します`);
  // シェルを通さずに渡す（本文の記号を解釈させない。T-48）
  execFileSync('gh', args, { stdio: 'inherit' });
  return 'attached';
}

export default function (gulp) {
  /**
   * 使い方: DEMO_BOLT=bolt-19 DEMO_ISSUE=8 npx gulp issue:attach-demo
   * DEMO_DRY_RUN=1 を付けると、gh を呼ばずに本文とコマンドを表示する（gh の版も確かめない）。
   * 同じ Bolt の目印のコメントがすでにあれば添付しない。
   */
  gulp.task('issue:attach-demo', (done) => {
    try {
      const dryRun = process.env.DEMO_DRY_RUN === '1';
      if (!dryRun) {
        console.log(`gh ${checkGhVersion()}`);
      }
      attach(process.env.DEMO_BOLT, process.env.DEMO_ISSUE, dryRun);
      done();
    } catch (error) {
      done(error);
    }
  });

  /**
   * これまでの Bolt の動画を、添付先の表（BOLT_ISSUES）のとおりにまとめて添付する。添付済みの Bolt は飛ばす。
   * 使い方: npx gulp issue:attach-demo:all（DEMO_DRY_RUN=1 で確かめる）
   */
  gulp.task('issue:attach-demo:all', (done) => {
    try {
      const dryRun = process.env.DEMO_DRY_RUN === '1';
      if (!dryRun) {
        console.log(`gh ${checkGhVersion()}`);
      }
      const results = Object.entries(BOLT_ISSUES).map(([bolt, issue]) => `${bolt} → #${issue}: ${attach(bolt, issue, dryRun)}`);
      console.log(results.join('\n'));
      done();
    } catch (error) {
      done(error);
    }
  });
}
