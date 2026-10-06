'use strict';

import path from 'path';
import { execSync } from 'child_process';
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
  deploy:demo:help          このヘルプを表示

環境変数（任意）: DEMO_HEROKU_APP、DEMO_HEROKU_REGION、DEMO_HEROKU_DYNO_TYPE、DEMO_JAVA_TOOL_OPTIONS、DEMO_SKIP_GUARD
手順書: docs/operation/cargo-tracker/heroku_demo_setup.md
`);
    done();
  });
}
