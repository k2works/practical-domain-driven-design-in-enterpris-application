'use strict';

/**
 * ステージング・本番の運用タスク（W10 の Bolt 54。ADR-020、bolt_54_plan.md の D-1〜D-5）。
 *
 * - DB への経路は SSM Session Manager の踏み台のポートフォワード（D-1）。手元の psql で操作する。踏み台と DB の宛先は環境変数で与える
 * - 本番は `--confirm production`（環境名の打ち直し）がないと動かない（D-2）。すべてのタスクが `--dry-run` で、実行する内容だけを表示する
 * - 名前は operation.md の運用タスクの一覧のとおり。環境は `--env staging|production`（D-5）
 *
 * このファイルのタスクは、実際の AWS 環境に対して**まだ一度も実行していない**（AWS のアカウントが未用意。ADR-020 の未決）。
 * 実行できるのは、引数の検査・確認の規則・ドライランの表示までで、テスト（operation.test.js）で確かめた。
 */

import { execFileSync, spawn } from 'child_process';
import crypto from 'crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'fs';
import net from 'net';
import os from 'os';
import path from 'path';

// ============================================
// 設定
// ============================================

/** 運用の対象の環境（ADR-020）。 */
export const ENVIRONMENTS = ['staging', 'production'];

const LOCAL_TUNNEL_PORT = 15432;
const DEFAULT_REGION = 'ap-northeast-1';
const APP_ROLE = 'cargo_tracker_app';
const MIGRATOR_ROLE = 'cargo_tracker_migrator';
/** 値を取らない旗。 */
const BOOLEAN_FLAGS = new Set(['dry-run']);

// ============================================
// 引数と設定の解決（テストできる純粋な関数）
// ============================================

/**
 * `--name value`・`--name=value`・`--flag` を読む。
 * @param {string[]} argv 引数（process.argv.slice(2) など）
 * @returns {Record<string, string | boolean>}
 */
export function parseOptions(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (!arg.startsWith('--')) continue;
    const body = arg.slice(2);
    const eq = body.indexOf('=');
    if (eq >= 0) {
      options[body.slice(0, eq)] = body.slice(eq + 1);
    } else if (BOOLEAN_FLAGS.has(body)) {
      // 真偽値の旗は値を取らない（`--dry-run staging` で後ろの引数を値として読み、ドライランが効かなくなるのを防ぐ）
      options[body] = true;
    } else if (argv[i + 1] !== undefined && !argv[i + 1].startsWith('--')) {
      options[body] = argv[++i];
    } else {
      options[body] = true;
    }
  }
  return options;
}

/**
 * 環境を解決する。環境名がなければ失敗し、本番は確認（`--confirm production`）がなければ失敗する（D-2）。
 * @param {Record<string, string | boolean>} options
 * @param {Record<string, string | undefined>} [env] 環境変数
 */
export function resolveTarget(options, env = process.env) {
  const target = options.env;
  if (!ENVIRONMENTS.includes(target)) {
    throw new Error(`--env に ${ENVIRONMENTS.join(' か ')} を指定してください（指定: ${String(target)}）`);
  }
  if (target === 'production' && options.confirm !== 'production') {
    throw new Error('本番の操作には `--confirm production`（環境名の打ち直し）が要ります');
  }
  const prefix = `OPS_${target.toUpperCase()}`;
  return {
    env: target,
    name: `cargo-tracker-${target}`,
    region: env.OPS_AWS_REGION || DEFAULT_REGION,
    dryRun: options['dry-run'] === true,
    bastionId: env[`${prefix}_BASTION_ID`],
    dbHost: env[`${prefix}_DB_HOST`],
    dbName: env[`${prefix}_DB_NAME`] || 'cargotracker',
    masterSecretArn: env[`${prefix}_DB_MASTER_SECRET_ARN`],
  };
}

/** 実行に要る環境変数が揃っているか。ドライランでは揃っていなくても表示だけは行う。 */
export function missingSettings(target, required) {
  const labels = { bastionId: 'BASTION_ID', dbHost: 'DB_HOST', masterSecretArn: 'DB_MASTER_SECRET_ARN' };
  return required.filter((key) => !target[key]).map((key) => `OPS_${target.env.toUpperCase()}_${labels[key]}`);
}

/**
 * 追記専用の表に、アプリケーション利用者が UPDATE・DELETE を持っていないかを調べる SQL（db:grants:verify）。
 * 1 行でも返れば違反。表の選び方はコールバック afterMigrate__grant_app_user.sql と同じ（表の印 [append-only]）。
 */
export function grantsVerifySql(appRole = APP_ROLE) {
  return `SELECT n.nspname || '.' || c.relname
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind = 'r'
   AND obj_description(c.oid, 'pg_class') LIKE '% [append-only]'
   AND (has_table_privilege('${appRole}', c.oid, 'UPDATE') OR has_table_privilege('${appRole}', c.oid, 'DELETE'))
 ORDER BY 1;`;
}

/**
 * 利用者の作成の SQL（db:init）。**Secrets Manager の値を正**とする: 利用者がなければ作り、あればパスワードを秘密に合わせる。
 * 途中で止まって再実行しても、秘密と DB のパスワードが食い違わない。パスワードは 16 進の文字だけ。
 */
export function initRolesSql(appPassword, migratorPassword, dbName) {
  const create = (role, password) => `DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role}') THEN
    CREATE ROLE ${role} LOGIN PASSWORD '${password}';
  ELSE
    ALTER ROLE ${role} PASSWORD '${password}';
  END IF;
END $$;`;
  return [
    create(APP_ROLE, appPassword),
    create(MIGRATOR_ROLE, migratorPassword),
    `GRANT CONNECT ON DATABASE ${dbName} TO ${APP_ROLE}, ${MIGRATOR_ROLE};`,
    `GRANT CREATE ON DATABASE ${dbName} TO ${MIGRATOR_ROLE};`,
  ].join('\n');
}

/** 戻す先のタスク定義を選ぶ。現在より 1 つ前の版（list の降順の次）。ないときは null。 */
export function previousTaskDefinition(currentArn, descendingArns) {
  const index = descendingArns.indexOf(currentArn);
  if (index < 0 || index + 1 >= descendingArns.length) return null;
  return descendingArns[index + 1];
}

// ============================================
// 実行の部品
// ============================================

function aws(target, args) {
  return execFileSync('aws', [...args, '--region', target.region], { encoding: 'utf8' }).trim();
}

/**
 * 秘密のパスワードを返す。あればそれを使い、なければ作る。作るときは、パスワードをプロセスの引数に出さないよう、
 * 一時ファイル（file://。作った利用者だけが読める）で渡し、すぐ消す。
 */
function getOrCreateSecretPassword(target, secretName, role, generatedPassword) {
  try {
    const existing = JSON.parse(
      aws(target, ['secretsmanager', 'get-secret-value', '--secret-id', secretName, '--query', 'SecretString', '--output', 'text']),
    );
    console.log(`  秘密 ${secretName} はあります。そのパスワードを使います`);
    return existing.password;
  } catch (error) {
    if (!/ResourceNotFoundException/.test(String(error.stderr ?? error.message))) throw error;
  }
  const directory = mkdtempSync(path.join(os.tmpdir(), 'ops-secret-'));
  const file = path.join(directory, 'secret.json');
  try {
    writeFileSync(file, JSON.stringify({ username: role, password: generatedPassword }), { mode: 0o600 });
    aws(target, ['secretsmanager', 'create-secret', '--name', secretName, '--secret-string', `file://${file.replaceAll('\\', '/')}`]);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
  console.log(`  秘密 ${secretName} を作りました`);
  return generatedPassword;
}

function describe(step) {
  console.log(`  [dry-run] ${step}`);
}

/** そのポートで何かが待ち受けているか。前回のトンネルの残りを、別の環境の DB への接続に使わないために、開く前に調べる。 */
export function isPortInUse(port) {
  return new Promise((resolve) => {
    const socket = net.connect(port, '127.0.0.1');
    socket.once('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.once('error', () => {
      socket.destroy();
      resolve(false);
    });
  });
}

/**
 * ポートがつながるまで待つ。トンネルのプロセスが失敗したら、上限を待たずにその理由で失敗する。
 * @param {number} port
 * @param {number} [timeoutMs]
 * @param {() => Error | undefined} [failure] トンネルの失敗（なければ undefined）を返す
 */
export function waitForPort(port, timeoutMs = 30000, failure = () => undefined) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const failed = failure();
      if (failed) {
        reject(failed);
        return;
      }
      const socket = net.connect(port, '127.0.0.1');
      socket.once('connect', () => {
        socket.destroy();
        resolve();
      });
      socket.once('error', () => {
        socket.destroy();
        if (Date.now() - startedAt > timeoutMs) reject(new Error('踏み台のポートフォワードがつながりません'));
        else setTimeout(attempt, 300);
      });
    };
    attempt();
  });
}

/**
 * 最初のシステム管理者の変数（admin:bootstrap）。メールアドレスの形と表示名の空を検査する。会社の名前の既定は「A 社」。
 * @param {Record<string, string | boolean>} options
 */
export function bootstrapVariables(options) {
  const email = typeof options.email === 'string' ? options.email.trim() : '';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new Error('--email に最初の管理者のメールアドレスを指定してください');
  }
  const name = typeof options.name === 'string' ? options.name.trim() : '';
  if (!name) throw new Error('--name に最初の管理者の表示名を指定してください');
  const company = typeof options['company-name'] === 'string' && options['company-name'].trim() ? options['company-name'].trim() : 'A 社';
  return { admin_email: email, admin_name: name, company_name: company };
}

/** psql の `-v name=value` の並び。 */
export function psqlVariableArgs(variables) {
  return Object.entries(variables).flatMap(([name, value]) => ['-v', `${name}=${value}`]);
}

/** GitHub Actions の実行の一覧（新しい順）の、最新の実行が成功か。古い成功を最新の失敗の代わりにしない。 */
export function latestSuccess(runs) {
  return runs.length > 0 && runs[0].conclusion === 'success';
}

/** トンネルを、aws が起こした session-manager-plugin の子ごと止める（Windows は kill が子を止めない）。 */
function stopTunnel(tunnel) {
  if (tunnel.pid === undefined) return;
  try {
    if (process.platform === 'win32') {
      execFileSync('taskkill', ['/pid', String(tunnel.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      process.kill(-tunnel.pid);
    }
  } catch {
    tunnel.kill();
  }
}

/**
 * SSM のポートフォワードを開き、psql で SQL を流す。パスワードは画面とログに出さない（環境変数 PGPASSWORD で渡す）。
 * @returns {Promise<string>} psql の標準出力
 */
async function psql(target, sql, variables = {}) {
  const missing = missingSettings(target, ['bastionId', 'dbHost', 'masterSecretArn']);
  if (missing.length > 0) throw new Error(`.env に次を設定してください: ${missing.join('、')}`);
  const secret = JSON.parse(
    aws(target, ['secretsmanager', 'get-secret-value', '--secret-id', target.masterSecretArn, '--query', 'SecretString', '--output', 'text']),
  );
  if (await isPortInUse(LOCAL_TUNNEL_PORT)) {
    throw new Error(
      `ローカルのポート ${LOCAL_TUNNEL_PORT} がすでに使われています（前回のトンネルの残りの恐れ。別の環境の DB につながるのを防ぐため止めます）。` +
        'session-manager-plugin のプロセスを確かめて止めてください',
    );
  }
  let tunnelFailure;
  const tunnel = spawn(
    'aws',
    [
      'ssm', 'start-session', '--target', target.bastionId,
      '--document-name', 'AWS-StartPortForwardingSessionToRemoteHost',
      '--parameters', `host=${target.dbHost},portNumber=5432,localPortNumber=${LOCAL_TUNNEL_PORT}`,
      '--region', target.region,
    ],
    { stdio: 'ignore', detached: process.platform !== 'win32' },
  );
  tunnel.once('error', (error) => {
    tunnelFailure = new Error(`トンネルを開けません（aws コマンドがあるか確かめる）: ${error.message}`);
  });
  tunnel.once('exit', (code) => {
    tunnelFailure ??= new Error(`トンネルが終了しました（終了コード ${code}）。認証（aws sso login など）と IAM の権限を確かめる`);
  });
  try {
    await waitForPort(LOCAL_TUNNEL_PORT, 30000, () => tunnelFailure);
    return execFileSync(
      'psql',
      ['-h', '127.0.0.1', '-p', String(LOCAL_TUNNEL_PORT), '-U', secret.username, '-d', target.dbName, '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', ...psqlVariableArgs(variables)],
      { input: sql, encoding: 'utf8', env: { ...process.env, PGPASSWORD: secret.password } },
    );
  } finally {
    stopTunnel(tunnel);
  }
}

// ============================================
// タスク
// ============================================

export default function (gulp) {
  // DB 利用者（マイグレーション用・アプリケーション用）の作成（DA-02）。パスワードを生成して Secrets Manager に置く。権限の付与は Flyway のコールバックが行う
  gulp.task('db:init', async () => {
    const target = resolveTarget(parseOptions(process.argv.slice(2)));
    const appPassword = crypto.randomBytes(24).toString('hex');
    const migratorPassword = crypto.randomBytes(24).toString('hex');
    console.log(`db:init ${target.env}: DB 利用者 ${APP_ROLE}・${MIGRATOR_ROLE} を作る（すでにあれば作らない）`);
    if (target.dryRun) {
      describe(`Secrets Manager に ${target.name}/db/app と ${target.name}/db/migrator を作る（username・password）`);
      describe('SSM のポートフォワードを開き、psql で利用者を作り、権限を与える');
      return;
    }
    // 秘密を正とする。すでにある秘密はそのパスワードを使い、なければ作る。途中で止まって再実行しても食い違わない
    const passwords = {};
    for (const [secretName, role, generated] of [
      [`${target.name}/db/app`, APP_ROLE, appPassword],
      [`${target.name}/db/migrator`, MIGRATOR_ROLE, migratorPassword],
    ]) {
      passwords[role] = getOrCreateSecretPassword(target, secretName, role, generated);
    }
    await psql(target, initRolesSql(passwords[APP_ROLE], passwords[MIGRATOR_ROLE], target.dbName));
    console.log('完了。application_db_secret_arn（Terraform の変数）に、作った app の Secrets Manager の ARN を渡す');
  });

  // 最初のシステム管理者を作る（H-1。ops/sql/admin_bootstrap.sql）。すでに管理者がいると失敗する。本人の password は「password の再設定」で設定する
  gulp.task('admin:bootstrap', async () => {
    const options = parseOptions(process.argv.slice(2));
    const target = resolveTarget(options);
    const variables = bootstrapVariables(options);
    console.log(`admin:bootstrap ${target.env}: 最初のシステム管理者 ${variables.admin_email}（${variables.admin_name}）を、企業「${variables.company_name}」に作る`);
    if (target.dryRun) {
      describe('すでにシステム管理者がいれば何もせず失敗する');
      describe('企業（なければ）・利用者（使えないハッシュ）・役割 SYSTEM_ADMIN・管理範囲（自社）・監査記録を 1 つのトランザクションで作る');
      describe('作った後、本人がログイン画面の「password を忘れた場合」から password を設定し、TOTP を登録する');
      return;
    }
    const sql = readFileSync(path.join('ops', 'sql', 'admin_bootstrap.sql'), 'utf8');
    await psql(target, sql, variables);
    console.log('作成した。本人に、ログイン画面の「password を忘れた場合」から password を設定してもらう');
  });

  // 追記専用の表の権限を確認する（読み取りだけ）
  gulp.task('db:grants:verify', async () => {
    const target = resolveTarget(parseOptions(process.argv.slice(2)));
    console.log(`db:grants:verify ${target.env}: 追記専用の表に ${APP_ROLE} の UPDATE・DELETE が付いていないかを確かめる`);
    if (target.dryRun) {
      describe(grantsVerifySql().replace(/\s+/g, ' '));
      return;
    }
    const violations = (await psql(target, grantsVerifySql())).trim();
    if (violations) throw new Error(`追記専用の表に UPDATE・DELETE の権限が残っています:\n${violations}`);
    console.log('違反なし');
  });

  // 配備前の確認（読み取りだけ）。コミット済み・CI が成功・ECS が安定しているか
  gulp.task('deploy:preflight', async () => {
    const target = resolveTarget(parseOptions(process.argv.slice(2)));
    console.log(`deploy:preflight ${target.env}`);
    if (target.dryRun) {
      describe('git の作業ツリーが clean で、HEAD が push 済みか');
      describe('gh run list --commit HEAD の cargo-tracker CI が成功か');
      describe(`aws ecs describe-services で ${target.name} の running と desired が同じか`);
      return;
    }
    const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim();
    const dirty = git('status', '--porcelain');
    if (dirty) throw new Error(`作業ツリーに未コミットの変更があります:\n${dirty}`);
    const head = git('rev-parse', 'HEAD');
    let upstream;
    try {
      upstream = git('rev-parse', '@{u}');
    } catch {
      throw new Error('HEAD の上流のブランチがありません（push されていません）');
    }
    if (head !== upstream) throw new Error('HEAD が push されていません（上流と違います）');
    const ci = execFileSync(
      'gh',
      ['run', 'list', '--workflow', 'cargo-tracker-ci.yml', '--commit', head, '--json', 'conclusion', '--limit', '5'],
      { encoding: 'utf8' },
    );
    if (!latestSuccess(JSON.parse(ci))) throw new Error('HEAD の cargo-tracker CI の最新の実行が成功していません');
    const service = JSON.parse(aws(target, ['ecs', 'describe-services', '--cluster', target.name, '--services', target.name, '--query', 'services[0]']));
    if (service.runningCount !== service.desiredCount) throw new Error(`ECS が安定していません（running ${service.runningCount} / desired ${service.desiredCount}）`);
    console.log('確認 OK（D-124〜D-172 の確認待ちの件数は人が台帳で確かめる）');
  });

  // 配備のワークフローを起動する薄いラッパー（配備の本体はワークフロー。本番へ昇格するときは --promote）
  gulp.task('deploy:release', async () => {
    const options = parseOptions(process.argv.slice(2));
    const target = resolveTarget(options);
    const ref = typeof options.ref === 'string' ? options.ref : 'develop';
    const promote = target.env === 'production';
    console.log(`deploy:release ${target.env}: ワークフロー cargo-tracker-deploy.yml を ref=${ref}、本番への昇格=${promote} で起動する`);
    const args = ['workflow', 'run', 'cargo-tracker-deploy.yml', '-f', `ref=${ref}`, '-f', `promote_to_production=${promote}`];
    if (target.dryRun) {
      describe(`gh ${args.join(' ')}`);
      return;
    }
    execFileSync('gh', args, { stdio: 'inherit' });
  });

  // 直前のタスク定義へ戻す（ECS のサーキットブレーカーの自動の戻しとは別の、人が判断する戻し）
  gulp.task('deploy:rollback', async () => {
    const target = resolveTarget(parseOptions(process.argv.slice(2)));
    console.log(`deploy:rollback ${target.env}: ${target.name} を直前のタスク定義に戻す`);
    if (target.dryRun) {
      describe(`aws ecs describe-services で現在のタスク定義を読み、list-task-definitions（降順）の次の版へ update-service し、services-stable を待つ`);
      return;
    }
    const current = aws(target, ['ecs', 'describe-services', '--cluster', target.name, '--services', target.name, '--query', 'services[0].taskDefinition', '--output', 'text']);
    const all = JSON.parse(aws(target, ['ecs', 'list-task-definitions', '--family-prefix', target.name, '--sort', 'DESC', '--query', 'taskDefinitionArns']));
    const previous = previousTaskDefinition(current, all);
    if (!previous) throw new Error('戻せる直前のタスク定義がありません');
    console.log(`現在 ${current} → 戻し先 ${previous}`);
    aws(target, ['ecs', 'update-service', '--cluster', target.name, '--service', target.name, '--task-definition', previous]);
    aws(target, ['ecs', 'wait', 'services-stable', '--cluster', target.name, '--services', target.name]);
    console.log('戻しを完了した');
  });
}
