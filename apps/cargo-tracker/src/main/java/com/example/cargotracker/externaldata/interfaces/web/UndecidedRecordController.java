package com.example.cargotracker.externaldata.interfaces.web;

import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.KeylessRejectionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.KeylessRejectionTarget;
import com.example.cargotracker.externaldata.application.internal.queryservices.QuarantineValues;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordPage;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-17 隔離・差異の確認（データ責任者。US-14 AC4。Bolt 39d）。判断を待つ隔離を取込の新しい順・ファイルの行の順に 100 件ずつ示す。取込で絞れ
 * （{@code ?ingestion=<取込 ID>}。S-16 の結果の画面の入口）、前後のページへ移れる（{@code ?page=<1 から>}）。トランザクションを持たず、照会の値を
 * 文言と項目に変えるだけで、並びとページの境（最後より後は最後のページ）を決めない。各行の「判断する」から判断の画面を開き、理由を書いて
 * 却下すると PRG で元の一覧に戻る（Bolt 39d2）。取込で絞った一覧に形式の誤りがあれば、まとめての却下の画面へのリンクを出し、その取込の
 * 形式の誤りを 1 つの理由でまとめて却下すると PRG で取込で絞った一覧に戻る（Bolt 39d2b）。ページ送りは表の前に置く（Bolt 39d2b）。
 * 判断できる受信記録の定めと却下は判断のサービスが持つ。内容の違う原本の判断の画面には、隔離の理由の後に受け取った値とこの外部版の採用値を
 * それぞれの取得した日時とともに並べ、値が同じなら取得時刻で分けた文を前に示す（航海は Bolt 39d3、【業務の判断】(i)・(j)。主要実績の値と
 * 値を読めないときの文は Bolt 39d3c、【業務の判断】(k)）。却下の欄の後に、採用できる隔離（判断のサービスの定め）にだけ採用の欄を示し、採用すると
 * PRG で元の一覧に戻る（航海は Bolt 39d3b。主要実績は 39d3b2）。
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

    private static final String DECISION_VIEW = "externaldata/undecided-records/decision";
    private static final String FORM = "rejectionForm";
    private static final String ADOPTION_FORM = "adoptionForm";
    private static final String REASON = "reason";
    private static final String RESULT = "result";

    /** 判断の後の知らせ（Bolt 39d2 の開始準備の決定 (5)）。 */
    private static final String REJECTED = "隔離を却下しました。";

    private static final String ADOPTED = "隔離を採用しました。";
    private static final String ALREADY_DECIDED = "この隔離は判断済みです。";

    /** 理由の欄の誤り（【業務の判断】(d)。必須、1000 文字まで）。 */
    private static final String REASON_REQUIRED = "却下の理由を書いてください。";

    private static final String REASON_TOO_LONG = "却下の理由は " + ReceivedRecordDecision.REASON_LIMIT + " 文字までで書いてください。";

    /** 採用の理由の欄の誤り（【業務の判断】(d)。任意、1000 文字まで。Bolt 39d3b）。 */
    private static final String ADOPTION_REASON_TOO_LONG =
            "採用の理由は " + ReceivedRecordDecision.REASON_LIMIT + " 文字までで書いてください。";

    /** 1 つの取込の形式の誤りのまとめての却下（Bolt 39d2b の開始準備の決定 (3)）。件数は 3 桁の区切り。 */
    private static final String KEYLESS_REJECTION_PATH = "/keyless-rejection";

    private static final String KEYLESS_REJECTION_VIEW = "externaldata/undecided-records/keyless-rejection";
    private static final String KEYLESS_REJECTION_LINK = "この取込の形式の誤りをまとめて却下する（%s 件）";
    private static final String KEYLESS_REJECTED = "形式の誤り %s 件を却下しました。";
    private static final String NOTHING_TO_REJECT = "この取込にまとめて却下できる形式の誤りはありません。";

    /** 行の範囲・外部 ID・外部版のないときの示し方。 */
    private static final String WHOLE_FILE = "ファイル全体";

    private static final String NO_EXTERNAL_KEY = "なし";
    private static final String NO_EXTERNAL_ID = "外部 ID なし";

    private final UndecidedRecordQueryService query;
    private final ReceivedRecordDecisionService decisions;

    public UndecidedRecordController(UndecidedRecordQueryService query, ReceivedRecordDecisionService decisions) {
        this.query = query;
        this.decisions = decisions;
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
        addCount(result, filtered, model);
        model.addAttribute(
                "rows",
                result.items().stream()
                        .map(item -> row(item, result.ingestionFilter(), result.page()))
                        .toList());
        model.addAttribute("pageSize", UndecidedRecordQueryService.PAGE_SIZE);
        if (result.hasPrevious()) {
            model.addAttribute("previousUrl", pageUrl(result.ingestionFilter(), result.page() - 1));
        }
        if (result.hasNext()) {
            model.addAttribute("nextUrl", pageUrl(result.ingestionFilter(), result.page() + 1));
        }
        return VIEW;
    }

    /**
     * 件数の文（0 件の文か、件数と示す範囲）と、取込で絞って形式の誤りがあるときのまとめての却下のリンク（Bolt 39d2b。押しても行き先のない
     * リンクは出さない）。
     */
    private static void addCount(UndecidedRecordPage result, boolean filtered, Model model) {
        if (result.total() == 0) {
            model.addAttribute("emptyText", filtered ? FILTERED_NONE : NONE);
            return;
        }
        model.addAttribute(
                "countText",
                (filtered ? FILTERED_COUNT : COUNT)
                        .formatted(grouped(result.total()), grouped(result.firstShown()), grouped(result.lastShown())));
        if (filtered && result.keylessQuarantineCount() > 0) {
            model.addAttribute("keylessRejectionUrl", keylessRejectionUrl(result.ingestionFilter()));
            model.addAttribute(
                    "keylessRejectionText", KEYLESS_REJECTION_LINK.formatted(grouped(result.keylessQuarantineCount())));
        }
    }

    /**
     * 判断の画面（Bolt 39d2）。判断できる受信記録（取込で書いた受信記録で、分類が隔離で、判断がない）の中身と隔離の理由の全文、却下の理由の欄を
     * 示す。UUID でない・ない・取込のない・隔離でない受信記録は 404、判断済みは元の一覧に戻って知らせる。{@code ingestion}・{@code page} は
     * 一覧に戻る先で、読めない値は捨てて既定にする。
     */
    @GetMapping("/{receivedRecordId}")
    public String decision(
            @PathVariable String receivedRecordId,
            @RequestParam(name = "ingestion", required = false) String ingestion,
            @RequestParam(name = "page", required = false) String page,
            Model model,
            RedirectAttributes redirectAttributes) {
        UUID id = parseUuid(receivedRecordId);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return decisionView(id, parseUuid(ingestion), parsePage(page), model, redirectAttributes);
    }

    /**
     * 却下する（Bolt 39d2）。理由が空・上限を超えれば判断の画面に戻って欄の誤りを示し、却下したら PRG で元の一覧に戻って知らせる。
     */
    @PostMapping("/{receivedRecordId}/decision")
    @SuppressWarnings("java:S107") // 画面の入力と戻り先と誤りと操作者を受ける MVC の入口
    public String reject(
            @PathVariable String receivedRecordId,
            @RequestParam(name = "ingestion", required = false) String ingestion,
            @RequestParam(name = "page", required = false) String page,
            @ModelAttribute(FORM) RejectionForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        UUID id = parseUuid(receivedRecordId);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        UUID back = parseUuid(ingestion);
        int backPage = parsePage(page);
        if (hasReasonErrors(form, bindingResult)) {
            return decisionView(id, back, backPage, model, redirectAttributes);
        }
        DecisionOutcome outcome = decisions.reject(actor, new RejectReceivedRecordCommand(id, form.getReason()));
        return respond(outcome, back, backPage, redirectAttributes);
    }

    /**
     * 採用する（Bolt 39d3b）。理由は任意で、前後の空白を除いて上限を超えれば判断の画面に戻って欄の誤りを示し、採用したら PRG で元の一覧に戻って知らせる。
     */
    @PostMapping("/{receivedRecordId}/adoption")
    @SuppressWarnings("java:S107") // 画面の入力と戻り先と誤りと操作者を受ける MVC の入口
    public String adopt(
            @PathVariable String receivedRecordId,
            @RequestParam(name = "ingestion", required = false) String ingestion,
            @RequestParam(name = "page", required = false) String page,
            @ModelAttribute(ADOPTION_FORM) AdoptionForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        UUID id = parseUuid(receivedRecordId);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        UUID back = parseUuid(ingestion);
        int backPage = parsePage(page);
        if (hasAdoptionReasonErrors(form, bindingResult)) {
            // 採用できない隔離の画面には採用の欄がなく、エラー要約のリンクの先がないので、欄の誤りを示さず採用の POST と同じ行き先にする
            Optional<DecisionOutcome> refusal = decisions.adoptionRefusalOf(id);
            return refusal.isPresent()
                    ? respond(refusal.get(), back, backPage, redirectAttributes)
                    : decisionView(id, back, backPage, model, redirectAttributes);
        }
        DecisionOutcome outcome = decisions.adopt(actor, new AdoptReceivedRecordCommand(id, form.getReason()));
        return respond(outcome, back, backPage, redirectAttributes);
    }

    /** 採用の理由の欄の誤り（任意、前後の空白を除いて上限まで）を足し、誤りがあれば真。 */
    private static boolean hasAdoptionReasonErrors(AdoptionForm form, BindingResult bindingResult) {
        String reason = form.getReason() == null ? "" : form.getReason().strip();
        if (reason.codePointCount(0, reason.length()) > ReceivedRecordDecision.REASON_LIMIT) {
            bindingResult.rejectValue(REASON, "reason.tooLong", ADOPTION_REASON_TOO_LONG);
        }
        return bindingResult.hasErrors();
    }

    /**
     * 1 つの取込の形式の誤りのまとめての却下の画面（Bolt 39d2b）。取込の時刻・情報源・判断を待つキーのない隔離の件数と、却下の理由の欄を示す。
     * 取込の指定がない・UUID でない・ない取込は 404、形式の誤りが 0 件なら取込で絞った一覧に戻って知らせる。判断の画面の
     * {@code /{receivedRecordId}} より文字の一致が先に選ばれる。
     */
    @GetMapping(KEYLESS_REJECTION_PATH)
    public String keylessRejection(
            @RequestParam(name = "ingestion", required = false) String ingestion,
            Model model,
            RedirectAttributes redirectAttributes) {
        return keylessRejectionView(requiredIngestion(ingestion), model, redirectAttributes);
    }

    /**
     * まとめて却下する（Bolt 39d2b）。理由が空・上限を超えれば、対象を読み直してまとめての却下の画面に戻り欄の誤りを示す（対象が 0 件・取込が
     * なければ画面と同じ行き先）。却下したら PRG で取込で絞った一覧の 1 ページ目に戻って、実際に却下した件数を知らせる。
     */
    @PostMapping(KEYLESS_REJECTION_PATH)
    public String rejectKeyless(
            @RequestParam(name = "ingestion", required = false) String ingestion,
            @ModelAttribute(FORM) RejectionForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        UUID ingestionId = requiredIngestion(ingestion);
        if (hasReasonErrors(form, bindingResult)) {
            return keylessRejectionView(ingestionId, model, redirectAttributes);
        }
        KeylessRejectionOutcome outcome =
                decisions.rejectKeyless(actor, new RejectKeylessQuarantinesCommand(ingestionId, form.getReason()));
        return respond(outcome, ingestionId, redirectAttributes);
    }

    /** 理由の欄の誤り（必須、前後の空白を除いて上限まで）を足し、誤りがあれば真（判断の画面とまとめての却下の画面で同じ）。 */
    private static boolean hasReasonErrors(RejectionForm form, BindingResult bindingResult) {
        String reason = form.getReason() == null ? "" : form.getReason().strip();
        if (reason.isEmpty()) {
            bindingResult.rejectValue(REASON, "reason.required", REASON_REQUIRED);
        } else if (reason.codePointCount(0, reason.length()) > ReceivedRecordDecision.REASON_LIMIT) {
            bindingResult.rejectValue(REASON, "reason.tooLong", REASON_TOO_LONG);
        }
        return bindingResult.hasErrors();
    }

    /** まとめての却下の画面を組み立てる。行き先と中身は照会で読む（ない取込は 404、0 件は一覧に戻る）。 */
    private String keylessRejectionView(UUID ingestionId, Model model, RedirectAttributes redirectAttributes) {
        KeylessRejectionTarget target = query.keylessRejectionTarget(ingestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (target.count() == 0) {
            return respond(new KeylessRejectionOutcome.NothingToReject(), ingestionId, redirectAttributes);
        }
        if (!model.containsAttribute(FORM)) {
            model.addAttribute(FORM, new RejectionForm());
        }
        model.addAttribute("ingestedAt", DateTimeDisplay.staff(target.ingestedAt()));
        model.addAttribute(
                "source", ExternalDataIngestionController.sourceLabel(target.provider(), target.recordType()));
        model.addAttribute("count", grouped(target.count()) + " 件");
        model.addAttribute("reasonLimit", ReceivedRecordDecision.REASON_LIMIT);
        model.addAttribute("backUrl", pageUrl(ingestionId, 1));
        model.addAttribute("actionUrl", keylessRejectionUrl(ingestionId));
        return KEYLESS_REJECTION_VIEW;
    }

    /** まとめての却下の結果を行き先に変える（結果のすべてに行き先がある。T-240）。一覧は取込で絞った 1 ページ目に戻る。 */
    private static String respond(
            KeylessRejectionOutcome outcome, UUID ingestionId, RedirectAttributes redirectAttributes) {
        return switch (outcome) {
            case KeylessRejectionOutcome.RejectedAll(int count) -> {
                redirectAttributes.addFlashAttribute(RESULT, KEYLESS_REJECTED.formatted(grouped(count)));
                yield "redirect:" + pageUrl(ingestionId, 1);
            }
            case KeylessRejectionOutcome.NothingToReject _ -> {
                redirectAttributes.addFlashAttribute(RESULT, NOTHING_TO_REJECT);
                yield "redirect:" + pageUrl(ingestionId, 1);
            }
            case KeylessRejectionOutcome.NotFound _ -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case KeylessRejectionOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    /** 取込の指定（UUID でない・ないときは 404。まとめての却下の画面は取込がないと示すものがない）。 */
    private static UUID requiredIngestion(String ingestion) {
        UUID ingestionId = parseUuid(ingestion);
        if (ingestionId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ingestionId;
    }

    /** まとめての却下の画面の URL（読んだ UUID だけから組む）。 */
    private static String keylessRejectionUrl(UUID ingestionId) {
        return LIST_URL + KEYLESS_REJECTION_PATH + "?ingestion=" + ingestionId;
    }

    /** 判断の画面を組み立てる。行き先は判断のサービスの判断できる受信記録の定めで決め、中身は照会で読む。 */
    private String decisionView(UUID id, UUID back, int backPage, Model model, RedirectAttributes redirectAttributes) {
        Optional<DecisionOutcome> refusal = decisions.refusalOf(id);
        if (refusal.isPresent()) {
            return respond(refusal.get(), back, backPage, redirectAttributes);
        }
        Optional<UndecidedRecordPage.Item> found = query.undecidedQuarantine(id);
        if (found.isEmpty()) {
            // 行き先を決めた後に判断された
            return respond(new DecisionOutcome.AlreadyDecided(), back, backPage, redirectAttributes);
        }
        UndecidedRecordPage.Item item = found.get();
        if (!model.containsAttribute(FORM)) {
            model.addAttribute(FORM, new RejectionForm());
        }
        if (!model.containsAttribute(ADOPTION_FORM)) {
            model.addAttribute(ADOPTION_FORM, new AdoptionForm());
        }
        // 採用の欄は判断のサービスの定めだけで決める（POST と同じ定め。照会の値からは決めない。Bolt 39d3b の開始準備の決定 (8)）
        model.addAttribute("adoptable", decisions.adoptionRefusalOf(id).isEmpty());
        String lines = lines(item.lineRange());
        String externalKey = externalKey(item);
        model.addAttribute("ingestedAt", DateTimeDisplay.staff(item.ingestedAt()));
        model.addAttribute("source", ExternalDataIngestionController.sourceLabel(item.provider(), item.recordType()));
        model.addAttribute("lines", lines == null ? WHOLE_FILE : lines);
        model.addAttribute("kind", item.keyless() ? FORMAT_ERROR : CONTENT_MISMATCH);
        model.addAttribute("externalKey", externalKey == null ? NO_EXTERNAL_KEY : externalKey);
        model.addAttribute("note", item.note());
        model.addAttribute("keyless", item.keyless());
        model.addAttribute("reasonLimit", ReceivedRecordDecision.REASON_LIMIT);
        model.addAttribute("backUrl", pageUrl(back, backPage));
        model.addAttribute("actionUrl", LIST_URL + "/" + id + "/decision" + query(back, backPage));
        model.addAttribute("adoptionActionUrl", LIST_URL + "/" + id + "/adoption" + query(back, backPage));
        query.quarantineValues(item).ifPresent(values -> addValues(values, model));
        return DECISION_VIEW;
    }

    /**
     * 受け取った値とこの外部版の採用値の領域（Bolt 39d3。示し方は {@link QuarantineValueViews}。Bolt 39d3c）。値が等しければ同じ値の文を示す。
     */
    private static void addValues(QuarantineValues values, Model model) {
        model.addAttribute("receivedValues", QuarantineValueViews.receivedOf(values));
        model.addAttribute("adoptedValues", QuarantineValueViews.adoptedOf(values));
        String sameValuesText = QuarantineValueViews.sameValuesTextOf(values);
        if (sameValuesText != null) {
            model.addAttribute("sameValuesText", sameValuesText);
        }
    }

    /** 判断の結果を行き先に変える（結果のすべてに行き先がある。T-240）。 */
    private static String respond(
            DecisionOutcome outcome, UUID back, int backPage, RedirectAttributes redirectAttributes) {
        return switch (outcome) {
            case DecisionOutcome.Rejected _ -> {
                redirectAttributes.addFlashAttribute(RESULT, REJECTED);
                yield "redirect:" + pageUrl(back, backPage);
            }
            case DecisionOutcome.AlreadyDecided _ -> {
                redirectAttributes.addFlashAttribute(RESULT, ALREADY_DECIDED);
                yield "redirect:" + pageUrl(back, backPage);
            }
            case DecisionOutcome.AdoptedQuarantine _ -> {
                redirectAttributes.addFlashAttribute(RESULT, ADOPTED);
                yield "redirect:" + pageUrl(back, backPage);
            }
            case DecisionOutcome.NotFound _, DecisionOutcome.NotDecidable _, DecisionOutcome.NotAdoptable _ ->
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case DecisionOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    private static Row row(UndecidedRecordPage.Item item, UUID ingestionFilter, int page) {
        String lines = lines(item.lineRange());
        String externalKey = externalKey(item);
        return new Row(
                DateTimeDisplay.staff(item.ingestedAt()),
                ExternalDataIngestionController.sourceLabel(item.provider(), item.recordType()),
                lines,
                item.keyless() ? FORMAT_ERROR : CONTENT_MISMATCH,
                externalKey,
                item.note(),
                LIST_URL + "/" + item.receivedRecordId() + query(ingestionFilter, page),
                (lines == null ? WHOLE_FILE : lines) + "、" + (externalKey == null ? NO_EXTERNAL_ID : externalKey));
    }

    /** 外部 ID・外部版（キーのない隔離は {@code null}）。 */
    private static String externalKey(UndecidedRecordPage.Item item) {
        return item.keyless() ? null : item.externalId() + "・" + item.externalVersion();
    }

    /** 行の範囲（「N 行目」「N〜M 行目」。行を持たないファイルの誤りは {@code null} で、画面は「—」を示す）。 */
    private static String lines(PhysicalLineRange range) {
        if (range == null) {
            return null;
        }
        return range.first() == range.last() ? range.first() + " 行目" : range.first() + "〜" + range.last() + " 行目";
    }

    private static String pageUrl(UUID ingestionFilter, int page) {
        return LIST_URL + query(ingestionFilter, page);
    }

    /** 一覧の取込とページの問い合わせ（読んだ UUID と数だけから組み、受け取った文字列をそのまま URL に入れない）。 */
    private static String query(UUID ingestionFilter, int page) {
        return "?" + (ingestionFilter == null ? "" : "ingestion=" + ingestionFilter + "&") + "page=" + page;
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
     * @param decisionUrl 判断の画面の URL（元の取込とページを持つ。Bolt 39d2）
     * @param decisionLabel 「判断する」の読み上げに足す行の範囲と外部 ID・外部版（Bolt 39d2）
     */
    record Row(
            String ingestedAt,
            String source,
            String lines,
            String kind,
            String externalKey,
            String note,
            String decisionUrl,
            String decisionLabel) {}
}
