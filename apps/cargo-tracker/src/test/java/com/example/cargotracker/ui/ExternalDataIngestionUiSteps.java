package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.rules.IngestionFileLimit;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * データ責任者の S-16 外部データの取込（選ぶ画面と結果の画面。US-14 AC1。Bolt 39c、上限を超えるファイルの拒否は Bolt 39c2、隔離の理由は Bolt 39c3、要確認は Bolt 39c4）の画面の層のステップ定義。承認済みの情報源はシナリオごとに
 * 提供元の名前にシナリオの UUID を含めて作り、選択肢を名前で選ぶ（共有の PostgreSQL のほかの情報源の数に左右されない。T-230）。ファイルは
 * 寄港 2 つ・時刻の順のドメインの規則に合う航海の行で作る（T-230）。ファイルの選択はブラウザのファイルの選択の窓を開かずに入れる。
 */
public class ExternalDataIngestionUiSteps {

    private static final String NEW_URL = "/staff/external-data/ingestions/new";
    private static final String FILE_LABEL = "取込ファイル（CSV、UTF-8）";
    private static final String COUNTS_CAPTION = "分類別の件数（受信記録の数）";
    /** 結果の画面の隔離の理由の見出しと説明（Bolt 39c3）。 */
    private static final String NOTES_HEADING = "隔離の理由";

    private static final String NOTES_ORDER = "ファイルの行の順に示します。";
    /** 結果の画面の要確認の見出しと案内の文（Bolt 39c4 の開始準備の決定 (4)）。 */
    private static final String REVIEW_HEADING = "要確認";

    private static final String REVIEW_GUIDE =
            "取り込めなかった部分があります。取り込めなかった部分の行は分類されていません。" + "下の「隔離の理由」で理由とどうすればよいかを確かめてください。";
    private static final String VOYAGE_HEADER = "external_id,external_version,acquired_at,port_calls";
    private static final String MILESTONE_HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";
    /** 寄港 2 つ（時刻の順）。内容の違う行は到着の時刻だけを変える。 */
    private static final String PORT_CALLS =
            "JPTYO;2026-12-01T00:00:00Z;2026-12-01T12:00:00Z|USLAX;2026-12-10T00:00:00Z;2026-12-10T12:00:00Z";

    private static final String CHANGED_PORT_CALLS =
            "JPTYO;2026-12-02T00:00:00Z;2026-12-02T12:00:00Z|USLAX;2026-12-10T00:00:00Z;2026-12-10T12:00:00Z";

    private final BrowserSession browser;
    private final JdbcTemplate jdbc;
    private final String baseUrl;
    private final String suffix = UUID.randomUUID().toString();
    /** 承認済みの航海の情報源の提供元（既定はシナリオの UUID を含む。マニュアルのキャプチャでは名前を指定する）。 */
    private String provider = "UI 海運-" + suffix;

    private UUID sourceId;
    /** 最後に選んだファイルの中身（同じファイルをもう一度選ぶため）。 */
    private byte[] lastFile;

    public ExternalDataIngestionUiSteps(
            BrowserSession browser,
            JdbcTemplate jdbc,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.jdbc = jdbc;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    /** 選択肢の名前（「提供元（原本種別の名前）」。Bolt 39c 計画の開始準備の決定 (4)）。S-17 の一覧の情報源の列も同じ名前（Bolt 39d）。 */
    String sourceLabel() {
        return provider + "（航海）";
    }

    /** 情報源の選定・承認は W10 の運用タスクで画面がないので、テストの準備として表に入れる。 */
    @前提("承認済みの航海の情報源がある")
    public void 承認済みの航海の情報源がある() {
        sourceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'VOYAGE', 'APPROVED', 0)",
                sourceId,
                provider);
    }

    /**
     * マニュアルのキャプチャ（{@code @manual}）に写る提供元の名前を指定して作る。名前は提供元と原本種別で一意（{@code uk_data_source}）
     * なので、1 回の実行で 1 つのシナリオだけが使う名前にする。
     */
    @前提("承認済みの航海の情報源 {string} がある")
    public void 名前を指定した承認済みの航海の情報源がある(String providerName) {
        provider = providerName;
        // DB を前の実行から引き継いだとき（同じコンテナや配備した環境に向けて撮り直すとき）は、後始末で停止中にした同じ名前の情報源が
        // 残っているので、入れずに承認済みに戻して使う（入れると uk_data_source に違反する）
        java.util.List<UUID> existing = jdbc.queryForList(
                "SELECT id FROM external_data.data_source WHERE provider = ? AND record_type = 'VOYAGE'",
                UUID.class,
                provider);
        if (existing.isEmpty()) {
            承認済みの航海の情報源がある();
            return;
        }
        sourceId = existing.get(0);
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'APPROVED' WHERE id = ?", sourceId);
    }

    /**
     * シナリオで作った情報源を停止中にする。情報源は取込が参照する（{@code fk_ingestion_data_source}）ので消さない。共有の PostgreSQL に
     * 承認済みのまま残すと、ほかのシナリオの選ぶ画面の選択肢が増え、マニュアルのキャプチャに写る（Bolt 39c ステップ 6）。前例は
     * {@link RoutingUiSteps} の航海の後片付け（作る手順と印のあるクラスに置く）。
     */
    @io.cucumber.java.After("@ui")
    public void シナリオの情報源を停止中にする() {
        if (sourceId == null) {
            return;
        }
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
    }

    /** データ責任者でログインし直してから、ホーム（{@code /}）を開く。ホームはデータ責任者を S-16 の選ぶ画面へ移す。 */
    @もし("データ責任者がホームを開く")
    public void データ責任者がホームを開く() {
        browser.navigate(baseUrl + NEW_URL);
        page().navigate(baseUrl + "/");
        page().waitForURL("**" + NEW_URL);
    }

    @ならば("外部データの取込の画面が開き、承認済みの航海の情報源を選べる")
    public void 外部データの取込の画面が開く() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("外部データの取込")))
                .isVisible();
        assertThat(sources()).isVisible();
        assertThat(sourceRadio()).isVisible();
        assertThat(page().getByLabel(FILE_LABEL)).isVisible();
        assertThat(page().getByText("1 ファイルにつきデータ行 10,000 行・40 MB まで。超えるときはファイルを分けて取り込んでください。"))
                .isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけで承認済みの航海の情報源を選び、航海 2 行のファイルを選んで取り込む")
    public void 航海2行のファイルを取り込む() {
        chooseSource();
        String acquiredAt = acquiredAt();
        chooseFile(
                voyageFile(voyageLine("V2611E", acquiredAt, PORT_CALLS), voyageLine("V2612E", acquiredAt, PORT_CALLS)));
        submit();
    }

    /**
     * 航海の 1 行（外部版 1）。外部 ID は航海番号で 30 文字まで。冪等性キーは提供元を含むが、名前を指定した情報源は前の実行から
     * 引き継ぐことがあるので、航海番号にもシナリオの印を付けて、撮り直しても分類別の件数が変わらないようにする（Bolt 39c ステップ 6）。
     */
    private String voyageLine(String voyageBase, String acquiredAt, String portCalls) {
        return voyageBase + "-" + suffix.substring(0, 8) + ",1," + acquiredAt + "," + portCalls;
    }

    /** Bolt 39c2 の行 22。物理データ行が上限（{@link IngestionFileLimit#MAX_DATA_LINES}）を 1 行超える航海のファイル。 */
    @かつ("キー操作だけで承認済みの航海の情報源を選び、データ行 10,001 行の航海のファイルを選んで取り込む")
    public void 上限を超えるファイルを取り込む() {
        chooseSource();
        String acquiredAt = acquiredAt();
        String[] lines = new String[IngestionFileLimit.MAX_DATA_LINES + 1];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = voyageLine("V%05d".formatted(i), acquiredAt, PORT_CALLS);
        }
        chooseFile(voyageFile(lines));
        submit();
    }

    @ならば("取込のエラー要約のリンクは取込ファイルの欄を指し、選んだファイルの選び直しを求める")
    public void エラー要約のリンクは取込ファイルの欄を指す() {
        Locator summary = errorSummary();
        assertThat(summary.getByRole(AriaRole.LINK)).hasAttribute("href", "#file");
        assertThat(summary).containsText("選んだファイルは残っていません。もう一度選んでください。");
        assertThat(page().locator("#file-error"))
                .hasText("ファイルが大きすぎます（1 ファイルにつきデータ行 10,000 行・40 MB まで）。ファイルを分けて取り込んでください。");
    }

    @ならば("情報源の取込はない")
    public void 情報源の取込はない() {
        // ここでは取込の件数だけを見る。原本を保存しないことは、セキュリティの統合テスト（行 1・2・5）で原本の保存の呼び出しの記録として確かめる
        Integer ingestions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM external_data.ingestion WHERE data_source_id = ?", Integer.class, sourceId);
        Assertions.assertThat(ingestions).isZero();
    }

    @もし("キー操作だけで承認済みの航海の情報源を選び、同じ外部 ID・外部版で内容の違う 2 行のファイルを選んで取り込む")
    public void 内容の違う2行のファイルを取り込む() {
        chooseSource();
        String acquiredAt = acquiredAt();
        chooseFile(voyageFile(
                voyageLine("V2611E", acquiredAt, PORT_CALLS), voyageLine("V2611E", acquiredAt, CHANGED_PORT_CALLS)));
        submit();
    }

    /**
     * Bolt 39c3 の行 9。航海の 6 行（採用 3・重複 1・列数の誤り 1・内容の違う原本 1）。2 行目と 5 行目は同じ内容（重複）、3 行目と 6 行目は
     * 同じ外部 ID・外部版で内容が違い（隔離）、4 行目は列が 1 つ多い（行の誤り）。
     */
    @かつ("キー操作だけで承認済みの航海の情報源を選び、正しい行と誤りの行の混ざった航海 6 行のファイルを選んで取り込む")
    public void 混ざったファイルを取り込む() {
        chooseSource();
        String acquiredAt = acquiredAt();
        chooseFile(voyageFile(
                voyageLine("V2611E", acquiredAt, PORT_CALLS),
                voyageLine("V2612E", acquiredAt, PORT_CALLS),
                voyageLine("V2613E", acquiredAt, PORT_CALLS) + ",余分",
                voyageLine("V2611E", acquiredAt, PORT_CALLS),
                voyageLine("V2612E", acquiredAt, CHANGED_PORT_CALLS),
                voyageLine("V2614E", acquiredAt, PORT_CALLS)));
        submit();
    }

    /**
     * Bolt 39d の行 5・18（S-17 のページ送り）。航海の 102 行（採用 1・内容の違う原本 1・列数の誤り 100）。2 行目と 3 行目は同じ外部 ID・外部版で
     * 内容が違い（3 行目が隔離）、4〜103 行目は列が 1 つ多い（行の誤り）。
     */
    @かつ("キー操作だけで承認済みの航海の情報源を選び、内容の違う原本 1 行と列の数の誤り 100 行を含む航海のファイルを選んで取り込む")
    public void 隔離が100件を超えるファイルを取り込む() {
        chooseSource();
        String acquiredAt = acquiredAt();
        String[] lines = new String[102];
        lines[0] = voyageLine("V2611E", acquiredAt, PORT_CALLS);
        lines[1] = voyageLine("V2611E", acquiredAt, CHANGED_PORT_CALLS);
        for (int i = 2; i < lines.length; i++) {
            lines[i] = voyageLine("V%05d".formatted(i), acquiredAt, PORT_CALLS) + ",余分";
        }
        chooseFile(voyageFile(lines));
        submit();
    }

    /** Bolt 39c3 の行 17。隔離が 0 件の結果の画面は「隔離の理由」の領域を出さない。 */
    @かつ("隔離の理由は示されない")
    public void 隔離の理由は示されない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(NOTES_HEADING)))
                .hasCount(0);
    }

    @かつ("隔離の理由に {int} 行目の内容の違う原本の注記だけが示される")
    public void 内容の違う原本の注記だけが示される(int lineNumber) {
        assertThat(notes()).hasText(new Pattern[] {contentMismatchNote(lineNumber)});
        browser.checkAccessibility();
    }

    @かつ("隔離の理由に 4 行目の列の数の誤りと 6 行目の内容の違う原本の注記がこの順に示され、重複の注記は示されない")
    public void 混ざったファイルの隔離の理由が行の順に示される() {
        assertThat(page().getByText(NOTES_ORDER)).isVisible();
        // 一覧の全体の一致で、重複の注記（「同じ原本を受け取り済みです」）を示さないことも確かめる（T-236）
        assertThat(notes()).hasText(new Pattern[] {
            Pattern.compile("^" + PlaywrightText.literal("4 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。") + "$"),
            contentMismatchNote(6)
        });
        browser.checkAccessibility();
    }

    /**
     * Bolt 39c4 の行 10〜13・17。取り込めなかった部分のあるファイル（0 バイト・選んだ情報源と違う種別・ヘッダーのない・閉じ忘れ）を取り込む。
     * 違う種別は主要実績の 2 行、ヘッダーのないファイルは航海のデータ行だけの 2 行（1 行目の値はどれも違い列の名前と重ならないので、必要な
     * 列がないになる。CsvHeader）、閉じ忘れは正しい航海 2 行の後の 4 行目で引用符を開いたまま航海 1 行を続ける（T-230）。
     */
    @かつ("キー操作だけで承認済みの航海の情報源を選び、取り込めなかった部分のある {string} を選んで取り込む")
    public void 取り込めなかった部分のあるファイルを取り込む(String kind) {
        chooseSource();
        String acquiredAt = acquiredAt();
        String first = voyageLine("V2611E", acquiredAt, PORT_CALLS);
        String second = voyageLine("V2612E", acquiredAt, PORT_CALLS);
        byte[] content =
                switch (kind) {
                    case "0 バイトのファイル" -> new byte[0];
                    case "主要実績のファイル" ->
                        (MILESTONE_HEADER + "\n"
                                        + milestoneLine("MS-1", acquiredAt) + "\n"
                                        + milestoneLine("MS-2", acquiredAt) + "\n")
                                .getBytes(StandardCharsets.UTF_8);
                    case "ヘッダーのないファイル" -> (first + "\n" + second + "\n").getBytes(StandardCharsets.UTF_8);
                    case "閉じ忘れのあるファイル" ->
                        voyageFile(
                                first,
                                second,
                                "V2613E-" + suffix.substring(0, 8) + ",1,\"" + acquiredAt + "," + PORT_CALLS,
                                voyageLine("V2614E", acquiredAt, PORT_CALLS));
                    default -> throw new IllegalArgumentException("知らないファイル: " + kind);
                };
        chooseFile(content);
        submit();
    }

    /** 主要実績の 1 行（発生の時刻は取得の時刻と同じ。外部 ID にシナリオの印を付ける）。 */
    private String milestoneLine(String base, String acquiredAt) {
        return base + "-" + suffix.substring(0, 8) + ",1," + acquiredAt + ",CTABCDEFGH2345,DEPARTURE,JPTYO,"
                + acquiredAt;
    }

    /** Bolt 39c4 の行 8。要確認でない結果の画面は「要確認」の領域を出さない。 */
    @かつ("要確認は示されない")
    public void 要確認は示されない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(REVIEW_HEADING)))
                .hasCount(0);
    }

    /**
     * Bolt 39c4 の行 10〜13・17。「要確認」の領域（見出し・案内の文・取り込めなかった部分）が、受け取った件数の後・分類別の件数の表の前に出る。
     */
    @ならば("分類別の件数の前に要確認と {string} が示される")
    public void 分類別の件数の前に要確認が示される(String region) {
        page().waitForURL(url -> url.matches(".*/staff/external-data/ingestions/[0-9a-f-]{36}$"));
        Locator review = page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName(REVIEW_HEADING));
        assertThat(review.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(REVIEW_HEADING)))
                .isVisible();
        assertThat(review).containsText(REVIEW_GUIDE);
        assertThat(review.getByText(region, new Locator.GetByTextOptions().setExact(true)))
                .isVisible();
        // 件数を読む前に要確認が分かるよう、受け取った件数の dl の後・分類別の件数の表の前に置く（開始準備の決定 (4)）
        Assertions.assertThat((Boolean) page().evaluate(
                                "([caption, heading]) => {"
                                        + " const table = [...document.querySelectorAll('table')]"
                                        + "   .find(t => t.caption && t.caption.textContent.trim() === caption);"
                                        + " const review = [...document.querySelectorAll('h2')]"
                                        + "   .find(h => h.textContent.trim() === heading).closest('section');"
                                        + " const dl = document.querySelector('main dl');"
                                        + " return !!(dl.compareDocumentPosition(review) & Node.DOCUMENT_POSITION_FOLLOWING)"
                                        + "   && !!(review.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING); }",
                                java.util.List.of(COUNTS_CAPTION, REVIEW_HEADING)))
                .as("要確認は受け取った件数の後・分類別の件数の表の前")
                .isTrue();
        browser.checkAccessibility();
    }

    /** Bolt 39c4 の行 10〜13。隔離の理由の一覧の全体が 1 件の注記（取り込めなかった部分の注記）だけである。 */
    @かつ("隔離の理由に {string} だけが示される")
    public void 隔離の理由に注記だけが示される(String note) {
        assertThat(notes()).hasText(new String[] {note});
        browser.checkAccessibility();
    }

    /** 隔離の理由の一覧の項目（見出しで名前の付いた一覧）。 */
    private Locator notes() {
        return page().getByRole(AriaRole.LIST, new Page.GetByRoleOptions().setName(NOTES_HEADING))
                .getByRole(AriaRole.LISTITEM);
    }

    /** 内容の違う原本の注記（最初の受信の時刻は取込の時刻で、秒までの UTC。IngestionService の注記の文）。 */
    private static Pattern contentMismatchNote(int lineNumber) {
        return Pattern.compile("^" + lineNumber + " 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: "
                + "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} UTC）。$");
    }

    @かつ("キー操作だけで承認済みの航海の情報源を選び、同じファイルをもう一度選んで取り込む")
    public void 同じファイルをもう一度取り込む() {
        chooseSource();
        chooseFile(lastFile);
        submit();
    }

    @もし("キー操作だけで何も選ばずに取り込む")
    public void 何も選ばずに取り込む() {
        submit();
    }

    @もし("キー操作だけで承認済みの航海の情報源だけを選んで取り込む")
    public void 情報源だけを選んで取り込む() {
        chooseSource();
        submit();
    }

    @ならば("取込の結果に知らせ {string} と情報源と受け取った件数 {int} が示される")
    public void 取込の結果が示される(String notice, int receivedCount) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("取込の結果")))
                .isVisible();
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(notice);
        assertThat(definition("情報源")).hasText(sourceLabel());
        assertThat(definition("取り込んだ日時")).containsText("Asia/Tokyo");
        assertThat(definition("受け取った件数（データ行）")).hasText(String.valueOf(receivedCount));
        browser.checkAccessibility();
    }

    @ならば("分類別の件数は採用 {int}・重複 {int}・隔離 {int} である")
    public void 分類別の件数(int adopted, int duplicate, int quarantined) {
        page().waitForURL(url -> url.matches(".*/staff/external-data/ingestions/[0-9a-f-]{36}$"));
        assertThat(count("採用")).hasText(String.valueOf(adopted));
        assertThat(count("重複")).hasText(String.valueOf(duplicate));
        assertThat(count("隔離")).hasText(String.valueOf(quarantined));
        // 差異の行は差異を作る 39h で足す（Bolt 39c 計画の開始準備の決定 (6)）
        assertThat(countsTable().locator("tbody tr")).hasCount(3);
        browser.checkAccessibility();
    }

    @もし("取込の結果の画面を読み込み直す")
    public void 読み込み直す() {
        page().reload();
        page().waitForLoadState();
    }

    @ならば("取込の結果に知らせは出ず、情報源の取込は 1 つのままである")
    public void 取り込み直さない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("取込の結果")))
                .isVisible();
        assertThat(page().getByRole(AriaRole.STATUS)).hasCount(0);
        Integer ingestions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM external_data.ingestion WHERE data_source_id = ?", Integer.class, sourceId);
        Assertions.assertThat(ingestions).isEqualTo(1);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで取込の結果から続けて取り込むを開く")
    public void 続けて取り込むを開く() {
        page().locator("body").focus();
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("続けて取り込む").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**" + NEW_URL);
        browser.checkAccessibility();
    }

    @ならば("取込のエラー要約にフォーカスが移り、{string} と {string} が示される")
    public void エラー要約に2件が示される(String first, String second) {
        Locator summary = errorSummary();
        assertThat(summary).isFocused();
        assertThat(summary.getByRole(AriaRole.HEADING)).hasText("取り込めませんでした");
        assertThat(summary.getByRole(AriaRole.LINK)).hasText(new String[] {first, second});
        assertThat(summary.getByRole(AriaRole.LINK).first()).hasAttribute("href", "#dataSourceId");
        assertThat(summary.getByRole(AriaRole.LINK).last()).hasAttribute("href", "#file");
        browser.checkAccessibility();
    }

    @ならば("取込のエラー要約にフォーカスが移り、{string} だけが示される")
    public void エラー要約に1件が示される(String only) {
        Locator summary = errorSummary();
        assertThat(summary.getByRole(AriaRole.LINK)).hasText(new String[] {only});
        assertThat(summary).isFocused();
        browser.checkAccessibility();
    }

    @ならば("承認済みの航海の情報源が選ばれたままである")
    public void 情報源が選ばれたまま() {
        assertThat(sourceRadio()).isChecked();
    }

    private Locator sources() {
        return page().getByRole(
                        AriaRole.GROUP,
                        new Page.GetByRoleOptions().setName("情報源").setExact(true));
    }

    private Locator sourceRadio() {
        return sources()
                .getByRole(
                        AriaRole.RADIO,
                        new Locator.GetByRoleOptions().setName(sourceLabel()).setExact(true));
    }

    private Locator errorSummary() {
        return page().locator("#error-summary");
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    private Locator countsTable() {
        return page().getByRole(AriaRole.TABLE, new Page.GetByRoleOptions().setName(COUNTS_CAPTION));
    }

    private Locator count(String classification) {
        return countsTable()
                .locator("tbody tr")
                .filter(new Locator.FilterOptions().setHas(page().locator("th:text-is('" + classification + "')")))
                .locator("td");
    }

    /**
     * 情報源のラジオボタンのグループに Tab で入り、矢印キーで自分の情報源を選ぶ（入ったときにフォーカスの当たる項目が自分の情報源なら
     * Space で選ぶ）。共有の DB のほかの情報源も並ぶので、選択肢の数だけ進める。
     */
    private void chooseSource() {
        page().locator("body").focus();
        Locator target = sourceRadio();
        Locator group = sources().getByRole(AriaRole.RADIO);
        browser.tabUntilGroup("情報源");
        int options = group.count();
        for (int i = 0;
                i < options && !(Boolean) target.evaluate("element => element === document.activeElement");
                i++) {
            page().keyboard().press("ArrowDown");
        }
        page().keyboard().press("Space");
        assertThat(target).isChecked();
    }

    private void chooseFile(byte[] content) {
        lastFile = content;
        page().getByLabel(FILE_LABEL).setInputFiles(new FilePayload("voyage-" + suffix + ".csv", "text/csv", content));
    }

    /** 「取り込む」へ Tab で進めて押し、POST の応答を受けて画面が移り終わるまで待つ（前例 SubmitTransportRequestUiSteps）。 */
    private void submit() {
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("取り込む").setExact(true)));
        page().waitForResponse(
                        response -> "POST".equals(response.request().method()),
                        () -> page().keyboard().press("Enter"));
        page().waitForLoadState();
    }

    private static byte[] voyageFile(String... lines) {
        return (VOYAGE_HEADER + "\n" + String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /** 取得時刻（取込の時刻より前。T-230）。 */
    private static String acquiredAt() {
        return Instant.now()
                .minus(30, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();
    }
}
