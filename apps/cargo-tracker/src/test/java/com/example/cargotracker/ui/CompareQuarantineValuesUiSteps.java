package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import java.util.regex.Pattern;

/**
 * データ責任者の S-17 隔離・差異の確認の判断の画面で、航海の内容の違う原本の受け取った値とこの外部版の採用値を見比べる（US-14 AC4、UC-15 の
 * 主成功シナリオ 5。Bolt 39d3、【業務の判断】(i)・(j)）画面の層のステップ定義。取り込みは S-16 のステップ定義
 * （{@link ExternalDataIngestionUiSteps}）、判断の画面を開くことと却下は判断の画面のステップ定義（{@link RejectQuarantineUiSteps}）が受け持ち、
 * この定義は値の領域（見出しで名前の付いた領域。取得した日時・情報版・公開した日時・航海番号・寄港の並び）と同じ値の文を確かめる。
 */
public class CompareQuarantineValuesUiSteps {

    private static final String RECEIVED_HEADING = "受け取った値";
    private static final String ADOPTED_HEADING = "この外部版の採用値";

    /** 社内の画面の日時（{@code DateTimeDisplay.staff} の形）。 */
    private static final String STAFF_TIME =
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} Asia/Tokyo（UTC\\+09:00）（UTC \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）";

    /** 同じ値の文の頭（値も取得時刻も同じとき、取得時刻だけが違うときの 2 つの文に共通）。 */
    private static final String SAME_VALUES_HEAD = "受け取った値はこの外部版の採用値と同じ";

    private final BrowserSession browser;

    public CompareQuarantineValuesUiSteps(BrowserSession browser) {
        this.browser = browser;
    }

    private Page page() {
        return browser.page();
    }

    /** Bolt 39d3 の行 9（受け取った値。取込ファイルの 3 行目）。 */
    @ならば("隔離の判断の画面の受け取った値に取得した日時と航海番号と東京の到着 {string} の寄港の並びが示される")
    public void 受け取った値が示される(String tokyoArrival) {
        Locator received = region(RECEIVED_HEADING);
        assertThat(received.getByText(Pattern.compile("^取得した日時 " + STAFF_TIME + "$")))
                .isVisible();
        assertPortCalls(received, tokyoArrival);
        browser.checkAccessibility();
    }

    /** Bolt 39d3 の行 9（この外部版の採用値。取込ファイルの 2 行目を取込で採用した値）。 */
    @かつ("隔離の判断の画面のこの外部版の採用値に情報版 {string} と公開した日時と取得した日時と東京の到着 {string} の寄港の並びが示される")
    public void この外部版の採用値が示される(String infoVersion, String tokyoArrival) {
        Locator adopted = region(ADOPTED_HEADING);
        assertThat(adopted.getByText(Pattern.compile("^情報版 " + PlaywrightText.literal(infoVersion) + "・公開した日時 "
                        + STAFF_TIME + "・取得した日時 " + STAFF_TIME + "$")))
                .isVisible();
        assertPortCalls(adopted, tokyoArrival);
        browser.checkAccessibility();
    }

    /** Bolt 39d3 の行 21・22（値が違えば同じ値の文を出さない）。 */
    @かつ("隔離の判断の画面に受け取った値とこの外部版の採用値が同じという文は示されない")
    public void 同じ値の文は示されない() {
        assertThat(page().getByText(SAME_VALUES_HEAD)).hasCount(0);
    }

    /** Bolt 39d3 の行 21（値も取得時刻も同じ）。文は 2 つの値の領域より前に置く。 */
    @ならば("隔離の判断の画面に {string} が示される")
    public void 文が示される(String text) {
        Locator sentence = page().getByText(text, new Page.GetByTextOptions().setExact(true));
        assertThat(sentence).isVisible();
        assertThat(region(RECEIVED_HEADING)).isVisible();
        assertThat(region(ADOPTED_HEADING)).isVisible();
        Object before = page().evaluate(
                        "([sentence, region]) => !!(sentence.compareDocumentPosition(region) & Node.DOCUMENT_POSITION_FOLLOWING)",
                        new Object[] {
                            sentence.elementHandle(), region(RECEIVED_HEADING).elementHandle()
                        });
        org.assertj.core.api.Assertions.assertThat(before).isEqualTo(true);
        browser.checkAccessibility();
    }

    /**
     * 航海番号（外部 ID の列の値。シナリオの印を付けた V2611E）と、寄港の番号付きの並び（運航の順に東京・ロサンゼルス。到着と出発を
     * {@code DateTimeDisplay.staff} の形で）。東京の到着だけが 2 行で違う（T-230）。
     */
    private static void assertPortCalls(Locator values, String tokyoArrival) {
        assertThat(values.locator("dt:text-is('航海番号') + dd")).hasText(Pattern.compile("^V2611E-[0-9a-f]{8}$"));
        Locator portCalls = values.getByRole(AriaRole.LIST).getByRole(AriaRole.LISTITEM);
        assertThat(portCalls).hasCount(2);
        assertThat(portCalls.nth(0))
                .hasText(Pattern.compile(
                        "^港 JPTYO・到着 " + PlaywrightText.literal(tokyoArrival) + " Asia/Tokyo（UTC\\+09:00）（UTC "
                                + "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）・出発 " + STAFF_TIME + "$"));
        assertThat(portCalls.nth(1))
                .hasText("港 USLAX・到着 2026-12-10 09:00 Asia/Tokyo（UTC+09:00）（UTC 2026-12-10 00:00）・出発 2026-12-10 21:00"
                        + " Asia/Tokyo（UTC+09:00）（UTC 2026-12-10 12:00）");
    }

    private Locator region(String heading) {
        return page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName(heading).setExact(true));
    }
}
