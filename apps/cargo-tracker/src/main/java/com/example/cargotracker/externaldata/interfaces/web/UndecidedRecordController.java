package com.example.cargotracker.externaldata.interfaces.web;

import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordPage;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordQueryService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * S-17 隔離・差異の確認（データ責任者。US-14 AC4。Bolt 39d）。判断を待つ隔離を取込の新しい順・ファイルの行の順に 100 件ずつ示す。取込で絞れ
 * （{@code ?ingestion=<取込 ID>}。S-16 の結果の画面の入口）、前後のページへ移れる（{@code ?page=<1 から>}）。トランザクションを持たず、照会の値を
 * 文言と項目に変えるだけで、並びとページの境（最後より後は最後のページ）を決めない。判断（却下・採用）は 39d2・39d3 で足す。
 *
 * <p>取込 ID が UUID でないときは 400 にせず、ない取込と同じく絞らない一覧に「指定された取込が見つかりません。」を示す。ページが数でない・1 より
 * 前のときは 1 ページ目を照会する（Bolt 39d 計画の状態の組み合わせの行 13・14）。
 */
@Controller
@RequestMapping(UndecidedRecordController.LIST_URL)
public class UndecidedRecordController {

    static final String LIST_URL = "/staff/external-data/undecided-records";

    private static final String VIEW = "externaldata/undecided-records/index";

    /** 件数と示す範囲の文（数は 3 桁の区切り。Bolt 39d 計画の開始準備の決定 (3)）。 */
    private static final String COUNT = "判断を待つ隔離 %s 件（%s〜%s 件目を示しています）";

    private static final String FILTERED_COUNT = "この取込の" + COUNT;
    private static final String NONE = "判断を待つ隔離はありません。";
    private static final String FILTERED_NONE = "この取込に判断を待つ隔離はありません。";
    private static final String INGESTION_NOT_FOUND = "指定された取込が見つかりません。";

    /** 種類の表示名（外部原本を持つ隔離と、外部原本も冪等性キーも持たないキーのない隔離）。 */
    private static final String CONTENT_MISMATCH = "内容の違う原本";

    private static final String FORMAT_ERROR = "形式の誤り";

    private final UndecidedRecordQueryService query;

    public UndecidedRecordController(UndecidedRecordQueryService query) {
        this.query = query;
    }

    /** 一覧。 */
    @GetMapping
    public String list(
            @RequestParam(name = "ingestion", required = false) String ingestion,
            @RequestParam(name = "page", required = false) String page,
            Model model) {
        UUID filter = parseUuid(ingestion);
        boolean malformed = filter == null && ingestion != null && !ingestion.isBlank();
        UndecidedRecordPage result = query.page(filter, parsePage(page));
        boolean filtered = result.ingestionFilter() != null;
        if (malformed || result.ingestionNotFound()) {
            model.addAttribute("ingestionNotFound", INGESTION_NOT_FOUND);
        }
        model.addAttribute("filtered", filtered);
        if (result.total() == 0) {
            model.addAttribute("emptyText", filtered ? FILTERED_NONE : NONE);
        } else {
            model.addAttribute(
                    "countText",
                    (filtered ? FILTERED_COUNT : COUNT)
                            .formatted(
                                    grouped(result.total()),
                                    grouped(result.firstShown()),
                                    grouped(result.lastShown())));
        }
        model.addAttribute(
                "rows",
                result.items().stream().map(UndecidedRecordController::row).toList());
        model.addAttribute("pageSize", UndecidedRecordQueryService.PAGE_SIZE);
        if (result.hasPrevious()) {
            model.addAttribute("previousUrl", pageUrl(result.ingestionFilter(), result.page() - 1));
        }
        if (result.hasNext()) {
            model.addAttribute("nextUrl", pageUrl(result.ingestionFilter(), result.page() + 1));
        }
        return VIEW;
    }

    private static Row row(UndecidedRecordPage.Item item) {
        return new Row(
                DateTimeDisplay.staff(item.ingestedAt()),
                ExternalDataIngestionController.sourceLabel(item.provider(), item.recordType()),
                lines(item.lineRange()),
                item.keyless() ? FORMAT_ERROR : CONTENT_MISMATCH,
                item.keyless() ? null : item.externalId() + "・" + item.externalVersion(),
                item.note());
    }

    /** 行の範囲（「N 行目」「N〜M 行目」。行を持たないファイルの誤りは {@code null} で、画面は「—」を示す）。 */
    private static String lines(PhysicalLineRange range) {
        if (range == null) {
            return null;
        }
        return range.first() == range.last() ? range.first() + " 行目" : range.first() + "〜" + range.last() + " 行目";
    }

    private static String pageUrl(UUID ingestionFilter, int page) {
        return LIST_URL + "?" + (ingestionFilter == null ? "" : "ingestion=" + ingestionFilter + "&") + "page=" + page;
    }

    private static String grouped(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    /** 数でない・1 より前のページは 1 ページ目（最後より後は照会が最後のページに寄せる）。 */
    private static int parsePage(String value) {
        if (value == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException _) {
            return 1;
        }
    }

    /**
     * 一覧の 1 行。
     *
     * @param ingestedAt 取り込んだ日時（社内の画面なので UTC を併記）
     * @param source 情報源の名前
     * @param lines 行の範囲の文（ファイルの誤りは {@code null}）
     * @param kind 種類
     * @param externalKey 外部 ID・外部版（キーのない隔離は {@code null}）
     * @param note 隔離の理由
     */
    record Row(String ingestedAt, String source, String lines, String kind, String externalKey, String note) {}
}
