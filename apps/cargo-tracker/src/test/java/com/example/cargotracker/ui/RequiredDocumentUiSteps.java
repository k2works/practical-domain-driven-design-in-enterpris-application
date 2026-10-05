package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;

/**
 * 必要書類（C-04 と S-03 の書類の一覧と取得）の画面の層のステップ定義。添付するファイルは {@code src/test/resources/ui-files} に置く。
 */
public class RequiredDocumentUiSteps {

    private final BrowserSession browser;

    public RequiredDocumentUiSteps(BrowserSession browser) {
        this.browser = browser;
    }

    /** テスト用のファイルの場所。 */
    static Path uiFile(String fileName) {
        try {
            return Path.of(RequiredDocumentUiSteps.class
                    .getResource("/ui-files/" + fileName)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private Page page() {
        return browser.page();
    }

    private Locator documents() {
        return page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName("必要書類").setExact(true));
    }

    @ならば("見積依頼の詳細の書類の一覧に {string} の {string} が表示される")
    @ならば("審査画面の書類の一覧に {string} の {string} が表示される")
    public void 書類の一覧に表示される(String type, String fileName) {
        Locator row = documents().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(fileName));
        assertThat(row).hasCount(1);
        assertThat(row).containsText(type);
    }

    @ならば("見積依頼の詳細の書類の一覧に {string} が表示されない")
    public void 書類の一覧に表示されない(String fileName) {
        assertThat(documents()).not().containsText(fileName);
    }

    @ならば("見積依頼の詳細から {string} を取得すると選んだファイルと同じ中身がダウンロードされる")
    @ならば("審査画面から {string} を取得すると選んだファイルと同じ中身がダウンロードされる")
    public void 取得すると同じ中身がダウンロードされる(String fileName) throws IOException {
        Locator link = documents().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(fileName));
        Download download = page().waitForDownload(link::click);
        Assertions.assertThat(download.suggestedFilename()).isEqualTo(fileName);
        Assertions.assertThat(Files.readAllBytes(download.path())).isEqualTo(Files.readAllBytes(uiFile(fileName)));
    }
}
