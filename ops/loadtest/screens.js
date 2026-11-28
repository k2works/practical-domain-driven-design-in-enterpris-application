// 画面の応答時間の負荷テスト（k6）。W10 の Bolt 55（持ち越し H-5）。ツールは k6（2026-11-16 に人が「推奨で一括承認」で選んだ）。
//
// 目標（非機能要件）: PERF-01 画面の表示 p95 1.0 秒・p99 2.0 秒、PERF-08 同時利用者 50 名で満たす、OBS-01 の警告 p95 1.5 秒。
//
// **このスクリプトは、まだ一度も実行していない**（手元に k6 がなく、ステージングもない。AWS は準備だけでよい決定）。
// ステージングができたら、まず少ない VU（--vus 2 --duration 30s）で試し、結果を手順書に書いてから 50 VU に上げる。
//
// 認証: ログインは TOTP を含むので、スクリプトでは行わない。人がブラウザでログインし、Cookie `SESSION` の値を環境変数で渡す
//   k6 run -e BASE_URL=https://<ステージングの ALB> -e SESSION_COOKIE=<SESSION の値> ops/loadtest/screens.js
// 対象の画面は、その利用者の役割で開ける画面（既定は荷主担当者の 3 画面。環境変数 PATHS にカンマ区切りで指定できる）。
// 読み取りだけを行う（POST はしない）。本番には向けない（ステージングだけ）。
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL;
const SESSION_COOKIE = __ENV.SESSION_COOKIE;
const PATHS = (__ENV.PATHS || '/customer/transport-requests,/customer/notifications,/customer/inquiries')
  .split(',')
  .map((path) => path.trim())
  .filter((path) => path.length > 0);

export const options = {
  scenarios: {
    screens: {
      executor: 'ramping-vus',
      startVUs: 1,
      // PERF-08: 同時利用者 50 名。1 分で上げ、3 分保ち、30 秒で下げる
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // PERF-01
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    // 画面が開けること（ログインへの転送を失敗として数える）
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (!BASE_URL || !SESSION_COOKIE) {
    throw new Error('BASE_URL と SESSION_COOKIE を -e で指定してください（本番には向けない）');
  }
  if (/production/i.test(BASE_URL)) {
    throw new Error('本番には向けません（ステージングだけ）');
  }
}

export default function () {
  for (const path of PATHS) {
    const response = http.get(`${BASE_URL}${path}`, {
      headers: { Cookie: `SESSION=${SESSION_COOKIE}` },
      redirects: 0,
      tags: { screen: path },
    });
    check(response, {
      '画面が開く（200。ログインへの転送や 403 は失敗）': (r) => r.status === 200,
    });
    sleep(1);
  }
}
