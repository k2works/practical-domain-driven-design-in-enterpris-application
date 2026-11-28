package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import java.util.regex.Pattern;

/**
 * データ責任者の S-17 判断の画面で、「この外部 ID の採用値」を読み、古い外部版の隔離を採用する前の警告を読む（US-14 AC4、UC-15 3b・5。
 * Bolt 39h3 の行 9。開始準備の決定 (3)・(4)）画面の層のステップ定義。取り込みは S-16 のステップ定義（{@link ExternalDataIngestionUiSteps}）、一覧と
 * 「この取込で絞る」は {@link UndecidedRecordUiSteps}、判断の画面を開くことは {@link RejectQuarantineUiSteps}、採用は {@link AdoptQuarantineUiSteps}
 * が受け持ち、この定義は領域と警告の中身と並びを確かめる。
 */
public class AdoptOldVersionQuarantineUiSteps {

    private static final String PARTNER_HEADING = "この外部 ID の採用値";
    private static final String ADOPTION_HEADING = "採用する";

    /** 社内の画面の日時（{@code DateTimeDisplay.staff} の形）。 */
    private static final String STAFF_TIME =
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} Asia/Tokyo（UTC\\+09:00）（UTC \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）";

    private static final String VOYAGE_WARNING =
            "この外部 ID は、この隔離を受け取った時刻より後に取得した外部版を採用しています。この隔離を採用すると、経路設計の航海がこの古い値に置き換わります"
                    + "（算出済みの候補の確定は拒否されることがあります）。「この外部 ID の採用値」と比べてから採用してください。";

    private final BrowserSession browser;

    public AdoptOldVersionQuarantineUiSteps(BrowserSession browser) {
        this.browser = browser;
    }

    private Page page() {
        return browser.page();
    }

    /** Bolt 39h3 の行 9。領域に最後に取得した採用値の情報版・公開した日時・取得した日時とロッテルダムの到着と説明の文が示される。 */
    @ならば("隔離の判断の画面のこの外部 ID の採用値に情報版 {string} と公開した日時と取得した日時とロッテルダムの到着 {string} が示される")
    public void この外部IDの採用値が示される(String infoVersion, String rotterdamArrival) {
        Locator region = page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName(PARTNER_HEADING).setExact(true));
        assertThat(region).isVisible();
        assertThat(region.getByText("取得した日時が最も新しい採用値です。", new Locator.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(region.getByText(Pattern.compile("^情報版 " + PlaywrightText.literal(infoVersion) + "・公開した日時 "
                        + STAFF_TIME + "・取得した日時 " + STAFF_TIME + "$")))
                .isVisible();
        assertThat(region.getByRole(AriaRole.LIST).getByRole(AriaRole.LISTITEM).nth(1))
                .hasText(Pattern.compile(
                        "^港 NLRTM・到着 " + PlaywrightText.literal(rotterdamArrival) + " \\d{2}:\\d{2} Asia/Tokyo"));
        browser.checkAccessibility();
    }

    /** Bolt 39h3 の行 9。警告は「採用する」の見出しの後・採用の理由の欄の前にあり、採用のボタンの説明に含まれ、採用の欄は今のまま使える。 */
    @かつ("隔離の判断の画面の採用するの欄の見出しの後に古い外部版の警告が示され採用のボタンの説明に含まれる")
    public void 警告が示される() {
        Locator warning =
                page().getByRole(AriaRole.NOTE).filter(new Locator.FilterOptions().setHasText("後に取得した外部版を採用しています"));
        assertThat(warning).hasCount(1);
        assertThat(warning).hasText(VOYAGE_WARNING);
        Locator heading = page().getByRole(
                        AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(ADOPTION_HEADING).setExact(true));
        Locator reason = page().getByLabel("採用の理由");
        Object ordered = page().evaluate(
                        "([heading, warning, reason]) => !!(heading.compareDocumentPosition(warning)"
                                + " & Node.DOCUMENT_POSITION_FOLLOWING)"
                                + " && !!(warning.compareDocumentPosition(reason) & Node.DOCUMENT_POSITION_FOLLOWING)",
                        new Object[] {heading.elementHandle(), warning.elementHandle(), reason.elementHandle()});
        org.assertj.core.api.Assertions.assertThat(ordered).isEqualTo(true);
        Locator button = page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(ADOPTION_HEADING).setExact(true));
        assertThat(button).hasAccessibleDescription(Pattern.compile("^採用すると取り消せません。.*後に取得した外部版を採用しています。.*"));
        assertThat(button).isEnabled();
        browser.checkAccessibility();
    }
}
