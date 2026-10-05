package com.example.cargotracker.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import org.springframework.beans.factory.DisposableBean;

/**
 * 画面の層の受入シナリオで使う Chromium（ヘッドレス）。起動は重いため、テストの Spring の文脈ごとに 1 つ作り、
 * 文脈を閉じるときに閉じる。Playwright はスレッドセーフではないため、シナリオを並列に動かすときは作り直す。
 */
public class PlaywrightBrowser implements DisposableBean {

    private final Playwright playwright = Playwright.create();
    private final Browser browser = playwright.chromium().launch();

    BrowserContext newContext() {
        return browser.newContext();
    }

    BrowserContext newContext(Browser.NewContextOptions options) {
        return browser.newContext(options);
    }

    @Override
    public void destroy() {
        browser.close();
        playwright.close();
    }
}
