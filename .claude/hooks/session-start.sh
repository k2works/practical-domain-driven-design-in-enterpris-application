#!/bin/bash
# Claude Code のクラウドの実行環境（Claude Code on the web）で、cargo-tracker の検証（check・uiTest・文書の検査）を
# 動かせる状態にする。手順と理由は docs/operation/cargo-tracker/application_development_setup.md の
# 「9. クラウドの実行環境」に書く（Bolt 11 の Try T-34）。何度動かしても同じ結果になるようにする。
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
APP="$ROOT/apps/cargo-tracker"
SUDO=""
if [ "$(id -u)" != "0" ] && command -v sudo >/dev/null 2>&1; then
  SUDO="sudo -n"
fi

log() { echo "[session-start] $*" >&2; }

# 1. 日本語のファイル名を扱うため UTF-8 のロケールにする（テストの報告の書き出しで失敗するため）
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  grep -q "LC_ALL=C.UTF-8" "$CLAUDE_ENV_FILE" 2>/dev/null || {
    echo 'export LC_ALL=C.UTF-8' >> "$CLAUDE_ENV_FILE"
    echo 'export LANG=C.UTF-8' >> "$CLAUDE_ENV_FILE"
  }
fi
export LC_ALL=C.UTF-8 LANG=C.UTF-8

# 2. JDK 25（Gradle の toolchain が求める）。ダウンロードのサイトは許可されていないため apt で入れる
if ! ls -d /usr/lib/jvm/java-25-* >/dev/null 2>&1; then
  log "JDK 25 を入れる"
  $SUDO apt-get update -q >/dev/null 2>&1 || true
  DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y -q openjdk-25-jdk-headless >/dev/null
fi

# 3. Docker（Testcontainers の PostgreSQL）。止まっていれば起こす
if command -v dockerd >/dev/null 2>&1 && ! docker info >/dev/null 2>&1; then
  log "dockerd を起動する"
  $SUDO sh -c 'nohup dockerd > /tmp/dockerd.log 2>&1 &'
  for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
fi

# 4. Gradle の依存（Maven Central が 429 を返すことがあるため、間を置いて取り直す）
if [ -x "$APP/gradlew" ]; then
  for attempt in 1 2 3 4; do
    if (cd "$APP" && ./gradlew testClasses --console=plain -q >/tmp/session-start-gradle.log 2>&1); then
      break
    fi
    if ! grep -q "429" /tmp/session-start-gradle.log; then
      log "Gradle の準備に失敗した（/tmp/session-start-gradle.log）"
      break
    fi
    log "Maven Central の 429。${attempt} 回目の取り直しを待つ"
    sleep $((attempt * 20))
  done
fi

# 5. Playwright のブラウザ。求める版のダウンロード元は許可されていないため、環境にある Chromium を求める版の場所から参照させる
BROWSERS="${PLAYWRIGHT_BROWSERS_PATH:-/opt/pw-browsers}"
DRIVER_JAR=$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.microsoft.playwright/driver" -name 'driver-*.jar' 2>/dev/null | sort | tail -1 || true)
if [ -n "$DRIVER_JAR" ] && [ -d "$BROWSERS" ]; then
  REVISION=$(unzip -p "$DRIVER_JAR" driver/package/browsers.json \
    | python3 -c "import sys,json;print(next(b['revision'] for b in json.load(sys.stdin)['browsers'] if b['name']=='chromium-headless-shell'))")
  WANTED="$BROWSERS/chromium_headless_shell-$REVISION"
  if [ ! -e "$WANTED/chrome-headless-shell-linux64/chrome-headless-shell" ]; then
    EXISTING=$(ls -d "$BROWSERS"/chromium_headless_shell-*/chrome-linux 2>/dev/null | sort | tail -1 || true)
    if [ -n "$EXISTING" ]; then
      log "Chromium $REVISION の代わりに $EXISTING を使う"
      $SUDO mkdir -p "$WANTED"
      $SUDO ln -sfn "$EXISTING" "$WANTED/chrome-headless-shell-linux64"
      $SUDO ln -sf headless_shell "$EXISTING/chrome-headless-shell"
      $SUDO touch "$WANTED/INSTALLATION_COMPLETE" "$WANTED/DEPENDENCIES_VALIDATED"
    fi
  fi
fi

# 6. 文書の検査（gulp okf:check など）の Node.js の依存
if [ -f "$ROOT/package.json" ] && [ ! -d "$ROOT/node_modules" ]; then
  log "npm install"
  (cd "$ROOT" && npm install --no-audit --no-fund >/dev/null 2>&1) || log "npm install に失敗した"
fi

# 7. SonarQube の Elasticsearch が求める vm.max_map_count（SonarQube を使うときだけ要る）
$SUDO sysctl -q -w vm.max_map_count=262144 >/dev/null 2>&1 || true

log "完了"
