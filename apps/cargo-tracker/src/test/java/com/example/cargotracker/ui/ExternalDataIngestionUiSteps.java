package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

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
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * データ責任者の S-16 外部データの取込（選ぶ画面と結果の画面。US-14 AC1。Bolt 39c）の画面の層のステップ定義。承認済みの情報源はシナリオごとに
 * 提供元の名前にシナリオの UUID を含めて作り、選択肢を名前で選ぶ（共有の PostgreSQL のほかの情報源の数に左右されない。T-230）。ファイルは
 * 寄港 2 つ・時刻の順のドメインの規則に合う航海の行で作る（T-230）。ファイルの選択はブラウザのファイルの選択の窓を開かずに入れる。
 */
public class ExternalDataIngestionUiSteps {

    private static final String NEW_URL = "/staff/external-data/ingestions/new";
    private static final String FILE_LABEL = "取込ファイル（CSV、UTF-8）";
    private static final String COUNTS_CAPTION = "分類別の件数（受信記録の数）";
    private static final String VOYAGE_HEADER = "external_id,external_version,acquired_at,port_calls";
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

    /** 選択肢の名前（「提供元（原本種別の名前）」。Bolt 39c 計画の開始準備の決定 (4)）。 */
    private String sourceLabel() {
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

    @もし("キー操作だけで承認済みの航海の情報源を選び、同じ外部 ID・外部版で内容の違う 2 行のファイルを選んで取り込む")
    public void 内容の違う2行のファイルを取り込む() {
        chooseSource();
        String acquiredAt = acquiredAt();
        chooseFile(voyageFile(
                voyageLine("V2611E", acquiredAt, PORT_CALLS), voyageLine("V2611E", acquiredAt, CHANGED_PORT_CALLS)));
        submit();
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
