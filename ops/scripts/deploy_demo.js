'use strict';

import path from 'path';
import { execFileSync, execSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/**
 * Heroku のデモ環境（ADR-013）。dev プロファイルのまま Container Registry で配備する。
 * 手順は docs/operation/cargo-tracker/heroku_demo_setup.md を参照。
 */
const APP = process.env.DEMO_HEROKU_APP || 'cargo-tracker-mono-demo';
const REGION = process.env.DEMO_HEROKU_REGION || 'us';
const DYNO_TYPE = process.env.DEMO_HEROKU_DYNO_TYPE || 'eco';
const APP_DIR = path.join(process.cwd(), 'apps', 'cargo-tracker');
const IMAGE = `registry.heroku.com/${APP}/web`;
const CI_WORKFLOW = 'cargo-tracker-ci.yml';
/** 配備してよいブランチ（ADR-013 の運用の約束） */
const DEPLOY_BRANCH = 'develop';
/** CI からの配備の API キー（Bolt 16、ADR-013、D-57） */
const REPO = 'k2works/practical-domain-driven-design-in-enterpris-application';
const CI_KEY_SECRET = 'HEROKU_API_KEY';
const CI_KEY_ENVIRONMENT = 'demo';
const CI_KEY_DESCRIPTION_PREFIX = 'GitHub Actions cargo-tracker demo deploy';
const CI_KEY_EXPIRES_DAYS = 90;
const CI_KEY_REMINDER_TITLE = '[運用] デモ環境の CI の API キーを更新する';
/** イメージに付けるコミットの SHA のラベル（OCI の注釈） */
const REVISION_LABEL = 'org.opencontainers.image.revision';

/**
 * Config Vars。プロファイルは dev だけにする（staging・prod を混ぜない。ADR-013 のコンプライアンス）。
 * JVM の値は Eco dyno（512 MB）で計測して決めた（Bolt 15 ステップ 2・3）。dyno では -XX:MaxRAMPercentage が
 * dyno の 512 MB ではなく大きなメモリを基準にして R14 になったため、ヒープは -Xmx で固定する（Heroku の Java の既定と同じ 300 MB）
 */
const CONFIG_VARS = {
  SPRING_PROFILES_ACTIVE: 'dev',
  JAVA_TOOL_OPTIONS:
    process.env.DEMO_JAVA_TOOL_OPTIONS ||
    '-Xmx300m -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m',
};

// ============================================
// ヘルパー関数
// ============================================

/**
 * コマンドを実行し、出力をそのまま表示する
 * @param {string} cmd - 実行するコマンド
 * @param {Object} [options] - execSync のオプション
 */
function run(cmd, options = {}) {
  console.log(`$ ${cmd}`);
  execSync(cmd, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

/**
 * コマンドを実行し、標準出力を文字列で返す
 * @param {string} cmd - 実行するコマンド
 * @returns {string}
 */
function capture(cmd) {
  return execSync(cmd, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], env: cleanDockerEnv() }).trim();
}

/**
 * シェルを通さずにコマンドを実行する（引数の中の記号を解釈させない）。標準出力を文字列で返す
 * @param {string} file - コマンド
 * @param {string[]} args - 引数
 * @param {Object} [options] - execFileSync のオプション（input など）
 * @returns {string}
 */
function execArgs(file, args, options = {}) {
  // 標準出力を端末に流す（stdio が inherit）ときは null が返るので、空文字にする
  const output = execFileSync(file, args, {
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'inherit'],
    env: cleanDockerEnv(),
    ...options,
  });
  return (output ?? '').trim();
}

/**
 * Heroku CLI にログインしているかを確かめる。していなければ止める
 */
function requireHerokuLogin() {
  try {
    capture('heroku auth:whoami');
  } catch {
    throw new Error('Heroku CLI にログインしていません。`heroku login` と `heroku container:login` を実行してください');
  }
}

/**
 * Heroku のアプリがあるかを返す
 * @returns {boolean}
 */
function appExists() {
  try {
    capture(`heroku apps:info -a ${APP} --json`);
    return true;
  } catch {
    return false;
  }
}

/**
 * 配備してよいコミットかを確かめる（ADR-013 の運用の約束。Bolt 15 レビュー R-03・R-07）。
 * - 作業ツリーに変更がない
 * - develop にいて、HEAD が origin/develop に含まれる（push 済み）
 * - アプリを最後に変えたコミットの、develop での CI が緑
 * 確かめを飛ばすときは DEMO_SKIP_GUARD=1 を付け、理由を終了報告かジャーナルに書く（R-25）
 */
function requireDeployableCommit() {
  if (process.env.DEMO_SKIP_GUARD === '1') {
    console.warn('DEMO_SKIP_GUARD=1: 作業ツリー・ブランチ・CI の確かめを飛ばします。理由を終了報告かジャーナルに書いてください');
    return;
  }
  if (capture('git status --porcelain')) {
    throw new Error('作業ツリーに変更があります。コミットしてから配備してください');
  }
  const branch = capture('git rev-parse --abbrev-ref HEAD');
  if (branch !== DEPLOY_BRANCH) {
    throw new Error(`${DEPLOY_BRANCH} から配備してください（いまは ${branch}）`);
  }
  capture(`git fetch --quiet origin ${DEPLOY_BRANCH}`);
  try {
    capture(`git merge-base --is-ancestor HEAD origin/${DEPLOY_BRANCH}`);
  } catch {
    throw new Error(`HEAD が origin/${DEPLOY_BRANCH} にありません。push して CI を待ってから配備してください`);
  }
  // CI はアプリの変更でだけ動くため、アプリを最後に変えたコミットの結果を見る
  const sha = capture('git log -1 --format=%H -- apps/cargo-tracker .github/workflows/cargo-tracker-ci.yml');
  const conclusion = capture(
    `gh run list --workflow ${CI_WORKFLOW} --branch ${DEPLOY_BRANCH} --commit ${sha} --limit 1 ` +
      `--json status,conclusion --jq 'if .[0] then (.[0].conclusion | if . == "" then "実行中" else . end) else "実行なし" end'`,
  );
  if (conclusion !== 'success') {
    throw new Error(`アプリの最後の変更 ${sha.slice(0, 8)} の CI が緑ではありません（${conclusion}）`);
  }
  console.log(`CI: ${sha.slice(0, 8)} は success（${DEPLOY_BRANCH}）`);
}

/**
 * HEAD のコミットの SHA を返す
 * @returns {string}
 */
function headRevision() {
  return capture('git rev-parse HEAD');
}

/**
 * 手元のデモのイメージに付いたコミットの SHA を返す。イメージがなければ空文字
 * @returns {string}
 */
function imageRevision() {
  try {
    return capture(`docker image inspect ${IMAGE} --format '{{index .Config.Labels "${REVISION_LABEL}"}}'`);
  } catch {
    return '';
  }
}

/**
 * デモのアプリの URL を返す。取れなければ案内して止める
 * @returns {string}
 */
function webUrl() {
  const url = JSON.parse(capture(`heroku apps:info -a ${APP} --json`))?.app?.web_url;
  if (!url) {
    throw new Error(`アプリ ${APP} の URL が取れません。\`npx gulp deploy:demo:setup\` でアプリを作ったか確かめてください`);
  }
  return url;
}

/**
 * CI からの配備のキー（説明が CI_KEY_DESCRIPTION_PREFIX で始まる authorization）を、新しい順に返す。
 * access_token などの値は返さない
 * @returns {{id: string, description: string, createdAt: string}[]}
 */
function ciKeys() {
  return JSON.parse(capture('heroku authorizations --json'))
    .filter((a) => (a.description || '').startsWith(CI_KEY_DESCRIPTION_PREFIX))
    .map((a) => ({ id: a.id, description: a.description, createdAt: a.created_at }))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

/**
 * 日付を YYYY-MM-DD で返す
 * @param {Date} date
 * @returns {string}
 */
function ymd(date) {
  return date.toISOString().slice(0, 10);
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // 初回のセットアップ。2 回目以降に実行しても、作成を飛ばして Config Vars を合わせるだけ
  gulp.task('deploy:demo:setup', (done) => {
    requireHerokuLogin();
    if (appExists()) {
      console.log(`アプリ ${APP} はあります。作成を飛ばします`);
    } else {
      run(`heroku apps:create ${APP} --stack container --region ${REGION}`);
    }
    const vars = Object.entries(CONFIG_VARS)
      .map(([key, value]) => `${key}="${value}"`)
      .join(' ');
    run(`heroku config:set ${vars} -a ${APP}`);
    // メモリの値（sample#memory_total）をログに出す。R14 の判断に使う（R-17）
    run(`heroku labs:enable log-runtime-metrics -a ${APP}`);
    done();
  });

  // demo のステージのイメージをビルドする。Heroku の Container Registry は Docker v2 の manifest だけを受けるため、
  // attestation（provenance・SBOM）を外し、OCI の media type を使わない（containerd のイメージストアでは既定が OCI。
  // 外さないと push が `error from registry: unsupported` になる。Bolt 15 ステップ 3）
  gulp.task('deploy:demo:build', (done) => {
    if (!isDockerAvailable()) {
      throw new Error('Docker が動いていません');
    }
    requireDeployableCommit();
    run(
      `docker buildx build --platform linux/amd64 --provenance=false --sbom=false --target demo ` +
        `--label ${REVISION_LABEL}=${headRevision()} ` +
        `--output type=image,name=${IMAGE},oci-mediatypes=false ${APP_DIR}`,
    );
    done();
  });

  // 単独で動かしても未検証のイメージを送らないよう、ここでも確かめる。手元のイメージが HEAD から作ったものであること（R-03）
  gulp.task('deploy:demo:push', (done) => {
    requireHerokuLogin();
    requireDeployableCommit();
    const revision = imageRevision();
    if (process.env.DEMO_SKIP_GUARD !== '1' && revision !== headRevision()) {
      throw new Error(`手元のイメージ（${revision.slice(0, 8) || 'なし'}）が HEAD から作ったものではありません。deploy:demo:build を先に実行してください`);
    }
    try {
      run(`docker push ${IMAGE}`);
    } catch (error) {
      throw new Error(`push に失敗しました。\`heroku container:login\` をしたか確かめてください（${error.message}）`);
    }
    done();
  });

  // release の後に、動いているコミットを Config Vars の DEMO_REVISION に残す（R-04。config:set で再起動が 1 回増える）
  gulp.task('deploy:demo:release', (done) => {
    requireHerokuLogin();
    const revision = imageRevision();
    run(`heroku container:release web -a ${APP}`);
    run(`heroku ps:type web=${DYNO_TYPE} -a ${APP}`);
    if (revision) {
      run(`heroku config:set DEMO_REVISION=${revision} -a ${APP}`);
    }
    done();
  });

  gulp.task('deploy:demo', gulp.series('deploy:demo:build', 'deploy:demo:push', 'deploy:demo:release'));

  gulp.task('deploy:demo:status', (done) => {
    requireHerokuLogin();
    run(`heroku ps -a ${APP}`);
    run(`heroku releases -n 5 -a ${APP}`);
    run(`heroku config:get SPRING_PROFILES_ACTIVE -a ${APP}`);
    console.log(`動いているコミット（DEMO_REVISION）: ${capture(`heroku config:get DEMO_REVISION -a ${APP}`) || '不明'}`);
    console.log(`URL: ${webUrl()}`);
    done();
  });

  gulp.task('deploy:demo:logs', (done) => {
    requireHerokuLogin();
    run(`heroku logs --tail -a ${APP}`);
    done();
  });

  // dyno を再起動し、データを db/dev-data の初期状態に戻す（デモの前に使う）
  gulp.task('deploy:demo:restart', (done) => {
    requireHerokuLogin();
    run(`heroku ps:restart web -a ${APP}`);
    done();
  });

  gulp.task('deploy:demo:open', (done) => {
    requireHerokuLogin();
    openUrl(webUrl());
    done();
  });

  // 止める（費用と公開を止める）。アプリの削除は手順書の手で行う（取り消せないため、タスクにしない）
  gulp.task('deploy:demo:stop', (done) => {
    requireHerokuLogin();
    run(`heroku ps:scale web=0 -a ${APP}`);
    done();
  });

  gulp.task('deploy:demo:start', (done) => {
    requireHerokuLogin();
    run(`heroku ps:scale web=1 -a ${APP}`);
    done();
  });

  // CI からの配備の API キーを作り、Environment demo の secret に登録する（Bolt 16 レビュー R-05、D-57）。
  // キーの値は表示しない。空なら登録しない。期限の 2 週間前を期日にした Issue を立て、前の Issue は閉じる。
  // 古いキーは、新しいキーで配備が通ったことを確かめてから deploy:demo:ci-key:revoke-old で失効させる
  gulp.task('deploy:demo:ci-key', (done) => {
    // 再入を止める（Bolt 16 で、Issue の本文のバッククォートをシェルが実行し、このタスクが入れ子で動き続けた）
    if (process.env.DEMO_CI_KEY_RUNNING === '1') {
      throw new Error('deploy:demo:ci-key が入れ子で呼ばれました。止めます');
    }
    process.env.DEMO_CI_KEY_RUNNING = '1';
    requireHerokuLogin();
    const now = new Date();
    const expires = new Date(now.getTime() + CI_KEY_EXPIRES_DAYS * 24 * 60 * 60 * 1000);
    const remind = new Date(expires.getTime() - 14 * 24 * 60 * 60 * 1000);
    const description = `${CI_KEY_DESCRIPTION_PREFIX} ${ymd(now)}`;
    // 外部コマンドはすべてシェルを通さずに渡す（Bolt 16 レビュー R-26 をこのタスクで先に行う）
    const token = execArgs(
      'heroku',
      ['authorizations:create', '-S', '-d', description, '-e', String(CI_KEY_EXPIRES_DAYS * 24 * 60 * 60)],
      { stdio: ['ignore', 'pipe', 'ignore'] },
    );
    if (token.length < 20) {
      throw new Error(`キーが取れませんでした（長さ ${token.length}）。登録しません`);
    }
    execArgs('gh', ['secret', 'set', CI_KEY_SECRET, '--env', CI_KEY_ENVIRONMENT, '--repo', REPO], { input: token });
    console.log(`キー「${description}」（長さ ${token.length}、期限 ${ymd(expires)}）を登録しました`);
    console.log(execArgs('gh', ['secret', 'list', '--env', CI_KEY_ENVIRONMENT, '--repo', REPO]));

    const open = execArgs('gh', [
      'issue', 'list', '--repo', REPO, '--state', 'open',
      '--search', `in:title ${CI_KEY_REMINDER_TITLE}`, '--json', 'number', '--jq', '.[].number',
    ]);
    for (const number of open.split('\n').filter(Boolean)) {
      execArgs('gh', ['issue', 'close', number, '--repo', REPO, '--comment', `キーを更新したため閉じます（${ymd(now)}）`]);
      console.log(`Issue #${number} を閉じました`);
    }
    const body = [
      `デモ環境の CI からの配備の API キー（${description}）の期限は ${ymd(expires)} です。`,
      `${ymd(remind)} までに、リポジトリのルートで \`npx gulp deploy:demo:ci-key\` を実行して更新してください。`,
      '手順: docs/operation/cargo-tracker/heroku_demo_setup.md の「CI からの配備の準備」',
    ].join('\n\n');
    const url = execArgs('gh', [
      'issue', 'create', '--repo', REPO, '--label', 'technical',
      '--title', `${CI_KEY_REMINDER_TITLE}（期日 ${ymd(remind)}）`, '--body', body,
    ]);
    console.log(`更新の Issue を立てました: ${url}`);
    console.log('次に、develop で CI を実行して配備が通ることを確かめてから、npx gulp deploy:demo:ci-key:revoke-old を実行してください');
    done();
  });

  // 最も新しいもの以外の CI の配備のキーを失効させる。新しいキーで配備が通ったことを確かめてから実行する
  gulp.task('deploy:demo:ci-key:revoke-old', (done) => {
    requireHerokuLogin();
    const [newest, ...old] = ciKeys();
    if (!newest) {
      throw new Error('CI の配備のキーがありません');
    }
    console.log(`残すキー: ${newest.description}（${newest.createdAt}）`);
    if (old.length === 0) {
      console.log('失効させる古いキーはありません');
    }
    for (const key of old) {
      console.log(`失効させる: ${key.description}（${key.createdAt}、${key.id}）`);
      execArgs('heroku', ['authorizations:revoke', key.id], { stdio: ['ignore', 'inherit', 'inherit'] });
    }
    done();
  });

  gulp.task('deploy:demo:help', (done) => {
    console.log(`
Heroku デモ環境（${APP}、ADR-013）

  deploy:demo:setup         アプリの作成と Config Vars の設定（2 回目以降は Config Vars だけ）
  deploy:demo:build         demo のステージのイメージをビルド（作業ツリーと CI の緑を確かめる）
  deploy:demo:push          イメージを Heroku の Container Registry にプッシュ
  deploy:demo:release       プッシュしたイメージを release し、dyno を ${DYNO_TYPE} にする
  deploy:demo               build → push → release
  deploy:demo:status        dyno・release・プロファイル・URL を表示
  deploy:demo:logs          ログを表示（tail）
  deploy:demo:restart       再起動してデータを初期状態に戻す
  deploy:demo:open          ブラウザで開く
  deploy:demo:stop          dyno を 0 にして止める
  deploy:demo:start         dyno を 1 にして動かす
  deploy:demo:ci-key        CI の配備の API キー（期限 ${CI_KEY_EXPIRES_DAYS} 日）を作って secret に登録し、更新の Issue を立てる
  deploy:demo:ci-key:revoke-old  最も新しいもの以外の CI の配備のキーを失効させる（新しいキーで配備が通った後）
  deploy:demo:help          このヘルプを表示

環境変数（任意）: DEMO_HEROKU_APP、DEMO_HEROKU_REGION、DEMO_HEROKU_DYNO_TYPE、DEMO_JAVA_TOOL_OPTIONS、DEMO_SKIP_GUARD
手順書: docs/operation/cargo-tracker/heroku_demo_setup.md
`);
    done();
  });
}
