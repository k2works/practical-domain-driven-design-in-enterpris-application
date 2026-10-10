'use strict';

import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/** 生成対象のサブシステム。サブシステムを足したら、こことサブシステムの docker-compose.yml を足す */
const SUBSYSTEMS = [
  {
    name: 'cargo-tracker',
    label: 'cargo-tracker',
    appDir: path.join('apps', 'cargo-tracker'),
    // postgres・flyway・schemaspy のサービスを定義した Compose ファイル
    composeFile: path.join('apps', 'cargo-tracker', 'docker-compose.yml'),
    dbName: 'cargotracker',
    dbUser: 'cargotracker',
    // 業務のスキーマ（データモデルのスキーマ分割）。ER 図の対象の一覧はここだけに書き、Compose の schemaspy へは環境変数
    // SCHEMASPY_SCHEMAS で渡す。スキーマを足したらここに足す（足し忘れは schemaspy:generate がマイグレーションの後に検出する）
    schemas: ['identity', 'quotation', 'routing', 'booking', 'tracking', 'platform'],
  },
];

/** ER 図（SchemaSpy）の出力先 */
function schemaspyOutputDir(sys) {
  return path.join(process.cwd(), 'docs', 'assets', 'schemaspy-output', sys.name);
}

/** JIG の出力先。Gradle の既定の出力（build/jig）をここへ写し、MkDocs から参照する */
function jigOutputDir(sys) {
  return path.join(process.cwd(), 'docs', 'assets', 'jig-output', sys.name);
}

// ============================================
// ヘルパー
// ============================================

/**
 * サブシステムの Compose ファイルで docker compose コマンドを実行する
 * @param {object} sys - サブシステム
 * @param {string} args - docker compose に渡す引数
 * @param {object} [options] - execSync のオプション
 */
function dockerCompose(sys, args, options = {}) {
  const env = { ...cleanDockerEnv(), SCHEMASPY_SCHEMAS: sys.schemas.join(',') };
  return execSync(`docker compose -f "${sys.composeFile}" ${args}`, { stdio: 'inherit', env, ...options });
}

/**
 * 業務のスキーマにある表の数を数える
 * @param {object} sys - サブシステム
 * @returns {number}
 */
function countTables(sys) {
  const schemaList = sys.schemas.map((s) => `'${s}'`).join(',');
  const sql = `SELECT count(*) FROM information_schema.tables WHERE table_schema IN (${schemaList})`;
  const out = dockerCompose(
    sys,
    `exec -T postgres psql -U ${sys.dbUser} -d ${sys.dbName} -tAc "${sql}"`,
    { stdio: ['ignore', 'pipe', 'inherit'] },
  );
  return Number.parseInt(String(out).trim(), 10) || 0;
}

/**
 * マイグレーションの後の DB にあって、ER 図の対象の一覧（sys.schemas）にない業務のスキーマを返す。
 * PostgreSQL のシステムのスキーマと、Flyway の履歴を置く public は除く（権限の統合テストの「業務のスキーマ」と同じ範囲）
 * @param {object} sys - サブシステム
 * @returns {string[]}
 */
function missingSchemas(sys) {
  const sql = "SELECT nspname FROM pg_namespace WHERE nspname NOT IN ('information_schema', 'public') AND left(nspname, 3) <> 'pg_' ORDER BY nspname";
  const out = dockerCompose(
    sys,
    `exec -T postgres psql -U ${sys.dbUser} -d ${sys.dbName} -tAc "${sql}"`,
    { stdio: ['ignore', 'pipe', 'inherit'] },
  );
  return String(out)
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s && !sys.schemas.includes(s));
}

/**
 * Gradle のタスクをサブシステムのディレクトリで実行する
 * @param {object} sys - サブシステム
 * @param {string} args - gradlew に渡す引数
 */
function gradle(sys, args) {
  // Windows の cmd は作業ディレクトリのバッチを探さない場合があるため、絶対パスで呼ぶ
  const appDir = path.join(process.cwd(), sys.appDir);
  const gradlew = path.join(appDir, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  execSync(`"${gradlew}" ${args}`, { cwd: appDir, stdio: 'inherit' });
}

/**
 * 生成物の index.html をブラウザで開く
 * @param {string} indexFile - 開くファイル
 * @param {string} generateTask - 先に実行するタスク名（案内用）
 */
function openGenerated(indexFile, generateTask) {
  if (!fs.existsSync(indexFile)) {
    throw new Error(`出力が見つかりません: ${indexFile}\n先に npx gulp ${generateTask} を実行してください。`);
  }
  openUrl(indexFile);
}

// ============================================
// タスク
// ============================================

/**
 * 設計ドキュメントの生成タスク（ER 図・JIG）を gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  // ──────────────────────────────────────────────
  // SchemaSpy ER 図生成
  // ──────────────────────────────────────────────

  gulp.task('schemaspy:generate', (done) => {
    if (!isDockerAvailable()) {
      done(new Error('Docker が起動していません。Docker を起動してから実行してください。'));
      return;
    }
    try {
      for (const sys of SUBSYSTEMS) {
        console.log(`=== ${sys.label} SchemaSpy ER 図生成 ===`);
        const outputDir = schemaspyOutputDir(sys);
        fs.rmSync(outputDir, { recursive: true, force: true });
        fs.mkdirSync(outputDir, { recursive: true });

        try {
          // 使い捨ての DB に、アプリと同じマイグレーションを当てる
          dockerCompose(sys, 'up -d --wait postgres');
          dockerCompose(sys, 'run --rm flyway');

          const tableCount = countTables(sys);
          if (tableCount === 0) {
            throw new Error(`マイグレーションの後も業務のスキーマ（${sys.schemas.join(', ')}）に表がありません`);
          }
          console.log(`  業務のスキーマの表の数: ${tableCount}`);
          // スキーマを足したときに一覧への追加が漏れると、ER 図からそのスキーマが黙って抜ける（routing・booking・tracking が抜けていた）
          const missing = missingSchemas(sys);
          if (missing.length > 0) {
            throw new Error(`ER 図の対象の一覧にない業務のスキーマがあります: ${missing.join(', ')}（design_docs.js の schemas に足してください）`);
          }

          dockerCompose(sys, 'run --rm schemaspy');
        } finally {
          // 毎回まっさらな DB から作るため、ボリュームごと消す
          dockerCompose(sys, 'rm -fsv postgres');
        }

        console.log(`  出力先: ${outputDir}`);
        console.log(`=== ${sys.label} SchemaSpy 完了 ===`);
      }
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('schemaspy:open', (done) => {
    try {
      SUBSYSTEMS.forEach((sys) => openGenerated(path.join(schemaspyOutputDir(sys), 'index.html'), 'schemaspy:generate'));
      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // JIG 設計ドキュメント生成
  // ──────────────────────────────────────────────

  gulp.task('jig:generate', (done) => {
    try {
      for (const sys of SUBSYSTEMS) {
        console.log(`=== ${sys.label} JIG 設計ドキュメント生成 ===`);
        gradle(sys, 'jigReports --no-daemon');

        const jigBuildDir = path.join(process.cwd(), sys.appDir, 'build', 'jig');
        if (!fs.existsSync(jigBuildDir)) {
          throw new Error(`JIG の出力が見つかりません: ${jigBuildDir}`);
        }

        const outputDir = jigOutputDir(sys);
        fs.rmSync(outputDir, { recursive: true, force: true });
        fs.mkdirSync(path.dirname(outputDir), { recursive: true });
        fs.cpSync(jigBuildDir, outputDir, { recursive: true });

        console.log(`  出力先: ${outputDir}`);
        console.log(`=== ${sys.label} JIG 完了 ===`);
      }
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('jig:open', (done) => {
    try {
      SUBSYSTEMS.forEach((sys) => openGenerated(path.join(jigOutputDir(sys), 'index.html'), 'jig:generate'));
      done();
    } catch (error) {
      done(error);
    }
  });

  // ──────────────────────────────────────────────
  // ドキュメントのビルドへの組み込み
  // ──────────────────────────────────────────────

  gulp.task('docs:generate', gulp.series('schemaspy:generate', 'jig:generate'));

  // mkdocs:build は mkdocs.js で登録する。gulpfile.js で mkdocs.js を先に読み込むこと
  gulp.task('docs:build', gulp.series('docs:generate', 'mkdocs:build'));
}
