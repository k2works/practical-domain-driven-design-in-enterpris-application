// 運用タスクの純粋な部分のテスト（引数の解釈・確認の規則・SQL・戻し先の選び方）。実行: node --test ops/scripts/operation.test.js
import assert from 'node:assert/strict';
import net from 'node:net';
import { test } from 'node:test';
import {
  resumeVariables,
  bootstrapVariables,
  psqlVariableArgs,
  grantsVerifySql,
  initRolesSql,
  isPortInUse,
  latestSuccess,
  missingSettings,
  waitForPort,
  parseOptions,
  previousTaskDefinition,
  resolveTarget,
} from './operation.js';

test('parseOptions は --name value・--name=value・--flag を読む', () => {
  assert.deepEqual(parseOptions(['--env', 'staging', '--confirm=production', '--dry-run']), {
    env: 'staging',
    confirm: 'production',
    'dry-run': true,
  });
});

test('resolveTarget は環境名がないか違うと失敗する', () => {
  assert.throws(() => resolveTarget({}), /--env/);
  assert.throws(() => resolveTarget({ env: 'prod' }), /--env/);
});

test('resolveTarget は本番に --confirm production がないと失敗する', () => {
  assert.throws(() => resolveTarget({ env: 'production' }), /--confirm production/);
  assert.throws(() => resolveTarget({ env: 'production', confirm: 'staging' }), /--confirm production/);
  assert.equal(resolveTarget({ env: 'production', confirm: 'production' }).name, 'cargo-tracker-production');
});

test('resolveTarget はステージングで確認なしに動き、環境変数を読む', () => {
  const target = resolveTarget(
    { env: 'staging', 'dry-run': true },
    { OPS_STAGING_BASTION_ID: 'i-123', OPS_STAGING_DB_HOST: 'db.internal', OPS_AWS_REGION: 'ap-northeast-1' },
  );
  assert.equal(target.dryRun, true);
  assert.equal(target.bastionId, 'i-123');
  assert.equal(target.dbHost, 'db.internal');
  assert.equal(target.dbName, 'cargotracker');
});

test('missingSettings は足りない環境変数の名前を返す', () => {
  const target = resolveTarget({ env: 'staging' }, { OPS_STAGING_BASTION_ID: 'i-1' });
  assert.deepEqual(missingSettings(target, ['bastionId', 'dbHost', 'masterSecretArn']), [
    'OPS_STAGING_DB_HOST',
    'OPS_STAGING_DB_MASTER_SECRET_ARN',
  ]);
});

test('grantsVerifySql は追記専用の印の表と UPDATE・DELETE の権限を見る', () => {
  const sql = grantsVerifySql('cargo_tracker_app');
  assert.match(sql, /\[append-only\]/);
  assert.match(sql, /has_table_privilege\('cargo_tracker_app'.*'UPDATE'/s);
  assert.match(sql, /'DELETE'/);
});

test('initRolesSql は秘密を正とし、利用者がなければ作り、あればパスワードを秘密に合わせる（再実行できる）', () => {
  const sql = initRolesSql('aa', 'bb', 'cargotracker');
  assert.match(sql, /IF NOT EXISTS \(SELECT 1 FROM pg_roles WHERE rolname = 'cargo_tracker_app'\)/);
  assert.match(sql, /CREATE ROLE cargo_tracker_app LOGIN PASSWORD 'aa'/);
  assert.match(sql, /ALTER ROLE cargo_tracker_app PASSWORD 'aa'/);
  assert.match(sql, /ALTER ROLE cargo_tracker_migrator PASSWORD 'bb'/);
});

test('initRolesSql はアプリケーション利用者だけに DB の 1 文の timeout（5 秒）を設定する', () => {
  const sql = initRolesSql('aa', 'bb', 'cargotracker');
  assert.match(sql, /ALTER ROLE cargo_tracker_app SET statement_timeout = '5s'/);
  assert.doesNotMatch(sql, /ALTER ROLE cargo_tracker_migrator SET statement_timeout/);
});

test('parseOptions は --dry-run のような真偽値の旗の後ろの引数を値として読まない', () => {
  assert.deepEqual(parseOptions(['--dry-run', 'staging', '--env', 'staging']), { 'dry-run': true, env: 'staging' });
});

test('isPortInUse は待ち受けているポートで true、空いているポートで false を返す', async () => {
  const server = net.createServer().listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  const port = server.address().port;
  assert.equal(await isPortInUse(port), true);
  await new Promise((resolve) => server.close(resolve));
  assert.equal(await isPortInUse(port), false);
});

test('waitForPort はトンネルの失敗を報告して、待ちきらずに失敗する', async () => {
  const started = Date.now();
  await assert.rejects(() => waitForPort(1, 30000, () => new Error('aws が見つからない')), /aws が見つからない/);
  assert.ok(Date.now() - started < 5000, '失敗は待ち時間の上限を待たずに返る');
});

test('bootstrapVariables は --email・--name を検査し、会社の名前の既定は A 社', () => {
  assert.deepEqual(bootstrapVariables({ email: 'admin@example.com', name: '管理者' }), {
    admin_email: 'admin@example.com',
    admin_name: '管理者',
    company_name: 'A 社',
  });
  assert.equal(bootstrapVariables({ email: 'a@b.co', name: 'x', 'company-name': '本社' }).company_name, '本社');
  assert.throws(() => bootstrapVariables({ name: 'x' }), /--email/);
  assert.throws(() => bootstrapVariables({ email: 'not-an-email', name: 'x' }), /--email/);
  assert.throws(() => bootstrapVariables({ email: 'a@b.co' }), /--name/);
  assert.throws(() => bootstrapVariables({ email: 'a@b.co', name: '   ' }), /--name/);
});

test('resumeVariables は --source-id（UUID）と --reason（1〜500 文字）を検査する', () => {
  const id = '3f2b8c1e-5d4a-4b7e-9a10-1c2d3e4f5a6b';
  assert.deepEqual(resumeVariables({ 'source-id': id, reason: '復旧した' }), { source_id: id, reason: '復旧した' });
  assert.throws(() => resumeVariables({ reason: 'x' }), /--source-id/);
  assert.throws(() => resumeVariables({ 'source-id': 'not-a-uuid', reason: 'x' }), /--source-id/);
  assert.throws(() => resumeVariables({ 'source-id': id }), /--reason/);
  assert.throws(() => resumeVariables({ 'source-id': id, reason: '  ' }), /--reason/);
  assert.throws(() => resumeVariables({ 'source-id': id, reason: 'あ'.repeat(501) }), /--reason/);
});

test('psqlVariableArgs は -v name=value の並びにする', () => {
  assert.deepEqual(psqlVariableArgs({ admin_email: 'a@b.co', admin_name: '管理者' }), [
    '-v', 'admin_email=a@b.co', '-v', 'admin_name=管理者',
  ]);
});

test('latestSuccess は先頭（最新）の実行が成功のときだけ true', () => {
  assert.equal(latestSuccess([{ conclusion: 'success' }, { conclusion: 'failure' }]), true);
  assert.equal(latestSuccess([{ conclusion: 'failure' }, { conclusion: 'success' }]), false);
  assert.equal(latestSuccess([{ conclusion: '' }]), false);
  assert.equal(latestSuccess([]), false);
});

test('previousTaskDefinition は降順の次の版を返し、なければ null', () => {
  const all = ['td:5', 'td:4', 'td:3'];
  assert.equal(previousTaskDefinition('td:5', all), 'td:4');
  assert.equal(previousTaskDefinition('td:3', all), null);
  assert.equal(previousTaskDefinition('td:9', all), null);
});
