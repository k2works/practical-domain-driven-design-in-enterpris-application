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
import net from 'net';

// ============================================
// 設定
// ============================================

/** 運用の対象の環境（ADR-020）。 */
export const ENVIRONMENTS = ['staging', 'production'];

const LOCAL_TUNNEL_PORT = 15432;
const DEFAULT_REGION = 'ap-northeast-1';
const APP_ROLE = 'cargo_tracker_app';
const MIGRATOR_ROLE = 'cargo_tracker_migrator';

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

/** 利用者の作成の SQL（db:init）。すでにあれば作らない（パスワードは変えない）。パスワードは 16 進の文字だけ。 */
export function initRolesSql(appPassword, migratorPassword, dbName) {
  const create = (role, password) => `DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role}') THEN
    CREATE ROLE ${role} LOGIN PASSWORD '${password}';
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

function describe(step) {
  console.log(`  [dry-run] ${step}`);
}

function waitForPort(port, timeoutMs = 30000) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const socket = net.connect(port, '127.0.0.1');
      socket.once('connect', () => {
        socket.destroy();
        resolve();
      });
      socket.once('error', () => {
        socket.destroy();
        if (Date.now() - startedAt > timeoutMs) reject(new Error('踏み台のポートフォワードがつながりません'));
        else setTimeout(attempt, 500);
      });
    };
    attempt();
  });
}

/**
 * SSM のポートフォワードを開き、psql で SQL を流す。パスワードは画面とログに出さない（環境変数 PGPASSWORD で渡す）。
 * @returns {Promise<string>} psql の標準出力
 */
async function psql(target, sql) {
  const missing = missingSettings(target, ['bastionId', 'dbHost', 'masterSecretArn']);
  if (missing.length > 0) throw new Error(`.env に次を設定してください: ${missing.join('、')}`);
  const secret = JSON.parse(
    aws(target, ['secretsmanager', 'get-secret-value', '--secret-id', target.masterSecretArn, '--query', 'SecretString', '--output', 'text']),
  );
  const tunnel = spawn(
    'aws',
    [
      'ssm', 'start-session', '--target', target.bastionId,
      '--document-name', 'AWS-StartPortForwardingSessionToRemoteHost',
      '--parameters', `host=${target.dbHost},portNumber=5432,localPortNumber=${LOCAL_TUNNEL_PORT}`,
      '--region', target.region,
    ],
    { stdio: 'ignore' },
  );
  try {
    await waitForPort(LOCAL_TUNNEL_PORT);
    return execFileSync(
      'psql',
      ['-h', '127.0.0.1', '-p', String(LOCAL_TUNNEL_PORT), '-U', secret.username, '-d', target.dbName, '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1'],
      { input: sql, encoding: 'utf8', env: { ...process.env, PGPASSWORD: secret.password } },
    );
  } finally {
    tunnel.kill();
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
    for (const [secretName, role, password] of [
      [`${target.name}/db/app`, APP_ROLE, appPassword],
      [`${target.name}/db/migrator`, MIGRATOR_ROLE, migratorPassword],
    ]) {
      aws(target, ['secretsmanager', 'create-secret', '--name', secretName, '--secret-string', JSON.stringify({ username: role, password })]);
    }
    await psql(target, initRolesSql(appPassword, migratorPassword, target.dbName));
    console.log('完了。application_db_secret_arn（Terraform の変数）に、作った app の Secrets Manager の ARN を渡す');
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
    const dirty = execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim();
    if (dirty) throw new Error(`作業ツリーに未コミットの変更があります:\n${dirty}`);
    const ci = execFileSync('gh', ['run', 'list', '--commit', execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(), '--json', 'conclusion,name'], { encoding: 'utf8' });
    if (!JSON.parse(ci).some((run) => run.conclusion === 'success')) throw new Error('HEAD の CI が成功していません');
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
