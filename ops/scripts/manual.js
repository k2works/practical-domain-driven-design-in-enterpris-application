'use strict';

import fs from 'fs';
import path from 'path';
import { marked } from 'marked';
import plantumlEncoder from 'plantuml-encoder';

/** 変換元（ユーザーマニュアルの Markdown）. */
const SRC_DIR = path.join(process.cwd(), 'docs', 'manual');
/**
 * 変換先（静的マニュアルサイト）。JIG・ER 図（SchemaSpy）と同じく docs/assets の下に出し（Git に入れない）、
 * MkDocs のサイトからリンクする（docs:generate で作る。2026-10-10 の人の指示）。`.env` の MANUAL_OUT_DIR で上書きできる.
 */
const OUT_DIR = process.env.MANUAL_OUT_DIR
  ? path.resolve(process.env.MANUAL_OUT_DIR)
  : path.join(process.cwd(), 'docs', 'assets', 'manual-output', 'cargo-tracker');
/** PlantUML レンダリングサーバ（mkdocs と同じ既定値）. */
const PLANTUML_SERVER = (
  process.env.PLANTUML_SERVER_URL || 'http://www.plantuml.com/plantuml'
).replace(/\/$/, '');
/** 先頭の YAML フロントマター（`---` で囲んだ行）. */
const FRONT_MATTER = /^---\r?\n[\s\S]*?\r?\n---\r?\n/;
/** マニュアルのサイトタイトル（`.env` の MANUAL_TITLE で上書きする）. */
const MANUAL_TITLE = process.env.MANUAL_TITLE || 'cargo-tracker ユーザーマニュアル';
/** フッターの著作権表示（未設定なら出力しない。`.env` の MANUAL_COPYRIGHT で指定する）. */
const MANUAL_COPYRIGHT = process.env.MANUAL_COPYRIGHT || '';
/** 上位ポータルへの戻り先（未設定ならヘッダーにリンクを出さない）. */
const MANUAL_PORTAL_URL = process.env.MANUAL_PORTAL_URL || '';

/**
 * 見出しテキストから HTML の id（アンカー）を生成する.
 *
 * マニュアル本文中の相互リンク（`#43-...` 等）と一致させるため、ドット・括弧・記号を除去し
 * 空白をハイフン、ASCII を小文字化する規則とする。
 * @param {string} text 見出しテキスト
 * @returns {string} アンカー id
 */
function slugify(text) {
  return text
    .trim()
    .replace(/[（）()【】「」、。，,：:・〜~/／]/g, '')
    .replace(/\./g, '')
    .replace(/\s+/g, '-')
    .toLowerCase();
}

/**
 * 先頭の YAML フロントマター（OKF の type・title など）を除く。HTML の本文には出さない.
 * @param {string} md Markdown 本文
 * @returns {string} フロントマターを除いた Markdown
 */
function stripFrontMatter(md) {
  return md.replace(FRONT_MATTER, '');
}

/**
 * ```plantuml フェンスを PlantUML サーバの SVG 画像 img に置き換える.
 * @param {string} md Markdown 本文
 * @returns {string} 置換後の Markdown（img は生 HTML）
 */
function renderPlantuml(md) {
  return md.replace(/```plantuml\r?\n([\s\S]*?)```/g, (_match, code) => {
    const encoded = plantumlEncoder.encode(code.trim());
    return `<p class="plantuml"><img src="${PLANTUML_SERVER}/svg/${encoded}" alt="PlantUML 図" loading="lazy"></p>`;
  });
}

/**
 * 同一フォルダの `.md` リンクを `.html` に書き換える（外部相対リンク `../` は対象外）.
 * @param {string} html HTML 文字列
 * @returns {string} 書き換え後 HTML
 */
function rewriteLinks(html) {
  return html.replace(
    /href="([^"/]+)\.md(#[^"]*)?"/g,
    (_m, file, anchor) => `href="${file}.html${anchor || ''}"`,
  );
}

/**
 * 見出しに id 属性を付与する（marked 既定では付かないため後処理で注入）.
 * @param {string} html HTML 文字列
 * @returns {string} id 付与後 HTML
 */
function injectHeadingIds(html) {
  return html.replace(/<h([1-6])>([\s\S]*?)<\/h\1>/g, (_m, level, inner) => {
    const text = inner.replace(/<[^>]+>/g, '');
    return `<h${level} id="${slugify(text)}">${inner}</h${level}>`;
  });
}

/**
 * 本文 HTML をページテンプレートで包む.
 * @param {string} title ページタイトル
 * @param {string} bodyHtml 本文 HTML
 * @param {boolean} isIndex 目次ページかどうか
 * @returns {string} 完全な HTML ドキュメント
 */
function pageTemplate(title, bodyHtml, isIndex, sideNav) {
  const tocLink = isIndex
    ? ''
    : '<a class="manual-nav-link" href="index.html">← マニュアル目次</a>';
  const footer = MANUAL_COPYRIGHT
    ? `&copy; ${new Date().getFullYear()} ${MANUAL_COPYRIGHT}. All rights reserved.`
    : MANUAL_TITLE;
  const portalLink = MANUAL_PORTAL_URL
    ? `\n    <a class="manual-portal" href="${MANUAL_PORTAL_URL}">ポータルへ戻る</a>`
    : '';
  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title} | ${MANUAL_TITLE}</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <header class="manual-header">
    <a class="manual-home" href="index.html">${MANUAL_TITLE}</a>${portalLink}
  </header>
  <div class="manual-layout">
    ${sideNav}
    <main class="manual-content">
      ${tocLink}
      ${bodyHtml}
      ${tocLink}
    </main>
  </div>
  <footer class="manual-footer">${footer}</footer>
  <script>
    // 画面の幅が狭いときは、目次を閉じた状態で始める（本文を先に見せる。「目次」で開ける）
    if (window.matchMedia('(max-width: 900px)').matches) {
      document.querySelectorAll('.manual-sidenav details').forEach((d) => d.removeAttribute('open'));
    }
  </script>
</body>
</html>
`;
}

/**
 * HTML の特殊文字をエスケープする.
 * @param {string} text 文字列
 * @returns {string} エスケープした文字列
 */
function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * ページの並びを決める。目次（index）を先頭に、番号で始まる章を番号の順に、残り（付録など）をタイトルの順に並べる.
 * @param {{file: string, title: string}[]} pages ページ
 * @returns {{file: string, title: string}[]} 並べたページ
 */
function orderPages(pages) {
  const rank = (page) => {
    if (page.file === 'index.md') return 0;
    return /^\d/.test(page.file) ? 1 : 2;
  };
  return [...pages].sort((a, b) => {
    const byRank = rank(a) - rank(b);
    if (byRank !== 0) return byRank;
    return rank(a) === 1 ? a.file.localeCompare(b.file) : a.title.localeCompare(b.title, 'ja');
  });
}

/**
 * 本文の h2 見出し（id とテキスト）を取り出す。サイドナビで開いているページの節に使う.
 * @param {string} html 見出しに id を付けた本文 HTML
 * @returns {{id: string, text: string}[]} 節
 */
function sectionsOf(html) {
  const sections = [];
  const pattern = /<h2 id="([^"]+)">([\s\S]*?)<\/h2>/g;
  let match;
  while ((match = pattern.exec(html)) !== null) {
    sections.push({ id: match[1], text: match[2].replace(/<[^>]+>/g, '') });
  }
  return sections;
}

/**
 * サイドナビ（全ページの目次）。開いているページは aria-current で示し、その節を下に並べる。
 * 画面の幅が狭いときは本文の上に置き、details で開閉できる（狭いときは閉じた状態で始める）.
 * @param {{file: string, title: string}[]} pages 並べたページ
 * @param {string} currentFile 開いているページの Markdown のファイル名
 * @param {{id: string, text: string}[]} sections 開いているページの節
 * @returns {string} サイドナビの HTML
 */
function sideNavigation(pages, currentFile, sections) {
  const items = pages
    .map((page) => {
      const href = encodeURI(path.basename(page.file, '.md') + '.html');
      const label = escapeHtml(page.file === 'index.md' ? '概要' : page.title);
      if (page.file !== currentFile) {
        return `<li><a href="${href}">${label}</a></li>`;
      }
      const sub = sections.length
        ? `<ul class="manual-sidenav-sections">${sections
            .map((section) => `<li><a href="#${encodeURI(section.id)}">${escapeHtml(section.text)}</a></li>`)
            .join('')}</ul>`
        : '';
      return `<li><a href="${href}" aria-current="page">${label}</a>${sub}</li>`;
    })
    .join('\n        ');
  return `<nav class="manual-sidenav" aria-label="マニュアルの目次">
      <details open>
        <summary>目次</summary>
        <ul>
        ${items}
        </ul>
      </details>
    </nav>`;
}

/** 生成する CSS（読みやすさ重視のシンプルなスタイル）. */
const STYLE_CSS = `:root { --fg: #24292f; --muted: #57606a; --border: #d0d7de; --accent: #1a237e; --bg-soft: #f6f8fa; }
* { box-sizing: border-box; }
body { margin: 0; color: var(--fg); font-family: -apple-system, "Segoe UI", "Hiragino Kaku Gothic ProN", Meiryo, sans-serif; line-height: 1.8; background: #fff; }
.manual-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: 0.75rem 1.5rem; background: var(--accent); color: #fff; position: sticky; top: 0; }
.manual-header a { color: #fff; text-decoration: none; }
.manual-home { font-weight: bold; }
.manual-portal { font-size: 0.85rem; opacity: 0.9; }
.manual-layout { display: flex; align-items: flex-start; max-width: 1240px; margin: 0 auto; }
.manual-sidenav { flex: 0 0 260px; position: sticky; top: 3.2rem; max-height: calc(100vh - 3.2rem); overflow-y: auto; padding: 1.5rem 0.5rem 2rem 1rem; border-right: 1px solid var(--border); font-size: 0.9rem; line-height: 1.6; }
.manual-sidenav summary { display: none; }
.manual-sidenav ul { list-style: none; margin: 0; padding: 0; }
.manual-sidenav li { margin: 0.15rem 0; }
.manual-sidenav a { display: block; padding: 0.2rem 0.5rem; border-radius: 4px; color: var(--fg); text-decoration: none; }
.manual-sidenav a:hover, .manual-sidenav a:focus-visible { background: var(--bg-soft); text-decoration: underline; }
.manual-sidenav a[aria-current="page"] { background: var(--accent); color: #fff; font-weight: bold; }
.manual-sidenav-sections { margin: 0.2rem 0 0.4rem 0.75rem !important; border-left: 2px solid var(--border); }
.manual-sidenav-sections a { color: var(--muted); font-size: 0.85rem; }
.manual-content { flex: 1 1 auto; min-width: 0; max-width: 900px; margin: 0 auto; padding: 2rem 1.5rem 4rem; }
@media (max-width: 900px) {
  .manual-layout { display: block; }
  .manual-sidenav { position: static; max-height: none; border-right: none; border-bottom: 1px solid var(--border); padding: 0.75rem 1rem; }
  .manual-sidenav summary { display: list-item; cursor: pointer; font-weight: bold; padding: 0.25rem 0; }
}
.manual-nav-link { display: inline-block; margin: 0.5rem 0; color: var(--accent); text-decoration: none; font-size: 0.9rem; }
.manual-content h1 { font-size: 1.8rem; border-bottom: 2px solid var(--border); padding-bottom: 0.3rem; }
.manual-content h2 { font-size: 1.4rem; border-bottom: 1px solid var(--border); padding-bottom: 0.3rem; margin-top: 2.5rem; }
.manual-content h3 { font-size: 1.15rem; margin-top: 2rem; }
.manual-content h4 { font-size: 1rem; color: var(--muted); }
.manual-content a { color: #0969da; }
.manual-content table { border-collapse: collapse; width: 100%; margin: 1rem 0; font-size: 0.95rem; }
.manual-content th, .manual-content td { border: 1px solid var(--border); padding: 0.4rem 0.6rem; text-align: left; }
.manual-content th { background: var(--bg-soft); }
.manual-content code { background: var(--bg-soft); padding: 0.1rem 0.3rem; border-radius: 4px; font-size: 0.9em; }
.manual-content pre { background: var(--bg-soft); padding: 1rem; border-radius: 6px; overflow-x: auto; }
.manual-content pre code { background: none; padding: 0; }
.manual-content blockquote { margin: 1rem 0; padding: 0.5rem 1rem; border-left: 4px solid var(--border); background: var(--bg-soft); color: var(--muted); }
.manual-content img { max-width: 100%; height: auto; border: 1px solid var(--border); border-radius: 4px; }
.manual-content .plantuml img { border: none; }
.manual-footer { text-align: center; padding: 1.5rem; font-size: 0.8rem; color: var(--muted); border-top: 1px solid var(--border); }
`;

/**
 * docs/manual の Markdown を HTML へ変換し docs/assets/manual-output/cargo-tracker へ出力する Gulp タスクを登録する.
 * @param {import('gulp').Gulp} gulp Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('manual:build', (done) => {
    try {
      if (!fs.existsSync(SRC_DIR)) {
        throw new Error(`マニュアルのソースが見つかりません: ${SRC_DIR}`);
      }

      // 出力先をクリーンして再作成
      fs.rmSync(OUT_DIR, { recursive: true, force: true });
      fs.mkdirSync(OUT_DIR, { recursive: true });

      // 画像アセットをコピー
      const srcAssets = path.join(SRC_DIR, 'assets');
      if (fs.existsSync(srcAssets)) {
        const outAssets = path.join(OUT_DIR, 'assets');
        fs.mkdirSync(outAssets, { recursive: true });
        for (const file of fs.readdirSync(srcAssets)) {
          if (/\.(png|jpe?g|gif|svg)$/i.test(file)) {
            fs.copyFileSync(path.join(srcAssets, file), path.join(outAssets, file));
          }
        }
      }

      // Markdown を HTML へ変換
      const mdFiles = fs
        .readdirSync(SRC_DIR)
        .filter((f) => f.endsWith('.md'))
        .sort();

      // サイドナビのため、先に全ページのタイトルと本文を読む
      const pages = mdFiles.map((mdFile) => {
        const raw = stripFrontMatter(fs.readFileSync(path.join(SRC_DIR, mdFile), 'utf8'));
        const titleMatch = raw.match(/^#\s+(.+)$/m);
        const title = titleMatch ? titleMatch[1].trim() : path.basename(mdFile, '.md');
        return { file: mdFile, title, raw };
      });
      const ordered = orderPages(pages);

      let converted = 0;
      for (const page of pages) {
        const bodyHtml = injectHeadingIds(rewriteLinks(marked.parse(renderPlantuml(page.raw))));
        const isIndex = page.file === 'index.md';
        const outName = path.basename(page.file, '.md') + '.html';
        const sideNav = sideNavigation(ordered, page.file, sectionsOf(bodyHtml));
        fs.writeFileSync(path.join(OUT_DIR, outName), pageTemplate(page.title, bodyHtml, isIndex, sideNav), 'utf8');
        converted += 1;
      }

      // スタイルシートを出力
      fs.writeFileSync(path.join(OUT_DIR, 'style.css'), STYLE_CSS, 'utf8');

      console.log(`マニュアルを HTML 変換しました: ${converted} ページ → ${OUT_DIR}`);
      done();
    } catch (error) {
      done(error);
    }
  });
}
