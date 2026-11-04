package com.example.cargotracker.externaldata.interfaces.web;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestibleDataSourceQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryView;
import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadiness;
import com.example.cargotracker.externaldata.domain.model.rules.IngestionFileLimit;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-16 外部データの取込（データ責任者。US-14 AC1・AC2。Bolt 39c）。データ責任者のホーム。承認済みの情報源を 1 つ選んで CSV ファイルを取り込み、
 * 結果の画面（PRG）に分類別の件数を示す。トランザクションを持たず、取込のサービスの結果を文言と行き先に変えるだけで、業務の判定（承認の状態・
 * 大きさ・分類）を持たない。
 *
 * <p>取込ファイルの種別は情報源の原本種別から決める（種別を別に選ばせない。Bolt 39c 計画の開始準備の決定 (3)）。情報源とファイルを選んで
 * いない誤りはサービスを呼ばずに選ぶ画面で示す。拒否（ない情報源・承認されていない情報源・大きすぎるファイル）は理由と次の行動を欄の誤りで
 * 示す（Bolt 39c2 の開始準備の決定 (2)）。上限の数は取込の規則（{@link IngestionFileLimit}）から作る（同 (3)）。
 */
@Controller
@RequestMapping("/staff/external-data/ingestions")
public class ExternalDataIngestionController {

    private static final String NEW_VIEW = "externaldata/ingestions/new";
    private static final String RESULT_VIEW = "externaldata/ingestions/result";
    private static final String FORM = "ingestionForm";
    private static final String DATA_SOURCE_ID = "dataSourceId";
    private static final String FILE = "file";

    /** エラー要約に出す項目の名前。文言は項目の名前で始めない（共通部品「エラー要約」）。 */
    private static final Map<String, String> FIELD_LABELS = Map.of(DATA_SOURCE_ID, "情報源", FILE, "取込ファイル");

    /** 承認済みの情報源がないときの全文（US-14 AC2。理由は開始判定の文言。Bolt 39c2 の開始準備の決定 (5)）。 */
    static final String NO_INGESTIBLE_SOURCE =
            "取り込める情報源がありません。" + PilotReadiness.NO_APPROVED_SOURCE + "。承認は運用の手順で行います。";

    /** 上限（「1 ファイルにつきデータ行 10,000 行・40 MB まで」）。数は取込の規則から作る（Bolt 39c2 の開始準備の決定 (3)）。 */
    private static final String LIMIT = "1 ファイルにつきデータ行 %s 行・%d MB まで"
            .formatted(
                    String.format(Locale.ROOT, "%,d", IngestionFileLimit.MAX_DATA_LINES),
                    IngestionFileLimit.MAX_BYTES / (1024 * 1024));

    /** 取込ファイルの欄の説明。 */
    private static final String FILE_HINT = LIMIT + "。超えるときはファイルを分けて取り込んでください。";

    /** 拒否の理由と次の行動（欄の誤り。Bolt 39c2 の開始準備の決定 (2)）。 */
    private static final String FILE_TOO_LARGE = "ファイルが大きすぎます（" + LIMIT + "）。ファイルを分けて取り込んでください。";

    private static final String SOURCE_NOT_APPROVED = "選んだ情報源は承認済みではないため取り込めません。画面を開き直して、承認済みの情報源を選んでください。";
    private static final String SOURCE_NOT_FOUND = "選んだ情報源が見つかりません。画面を開き直して、情報源を選び直してください。";

    /** 原本種別（取込ファイルの種別）の表示名。 */
    private static final Map<CsvFileKind, String> KIND_LABELS =
            Map.of(CsvFileKind.VOYAGE, "航海", CsvFileKind.MILESTONE, "主要実績");

    private final IngestionService ingestionService;
    private final IngestibleDataSourceQueryService dataSources;
    private final IngestionSummaryQueryService summaries;

    public ExternalDataIngestionController(
            IngestionService ingestionService,
            IngestibleDataSourceQueryService dataSources,
            IngestionSummaryQueryService summaries) {
        this.ingestionService = ingestionService;
        this.dataSources = dataSources;
        this.summaries = summaries;
    }

    /** 選ぶ画面。承認済みの情報源がなければ、取り込めない理由と次の行動だけを示す（US-14 AC2）。 */
    @GetMapping("/new")
    public String newIngestion(Model model) {
        return form(new IngestionForm(), model);
    }

    /**
     * 取り込む（US-14 AC1）。取り込んだら結果の画面へ PRG で移り、知らせを示す（結果の画面を読み込み直しても取り込み直さない）。
     */
    @PostMapping
    public String ingest(
            @ModelAttribute(FORM) IngestionForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        boolean sourceChosen =
                form.getDataSourceId() != null && !form.getDataSourceId().isBlank();
        if (!sourceChosen) {
            bindingResult.rejectValue(DATA_SOURCE_ID, "dataSourceId.required", "取り込む情報源を 1 つ選んでください。");
        }
        if (!fileChosen(form.getFile())) {
            bindingResult.rejectValue(FILE, "file.required", "取り込むファイルを選んでください。");
        }
        if (bindingResult.hasErrors()) {
            return rejected(form, model);
        }
        Optional<UUID> sourceId = parse(form.getDataSourceId());
        Optional<CsvFileKind> kind =
                sourceId.flatMap(dataSources::recordTypeOf).flatMap(ExternalDataIngestionController::kindOf);
        if (sourceId.isEmpty() || kind.isEmpty()) {
            return sourceRefused(form, bindingResult, "dataSourceId.notFound", SOURCE_NOT_FOUND, model);
        }
        IngestFileCommand command =
                IngestFileCommandAssembler.assemble(sourceId.get(), kind.get(), bytesOf(form.getFile()));
        return respond(ingestionService.ingest(actor, command), form, bindingResult, model, redirectAttributes);
    }

    /** 取込のサービスの結果を行き先に変える。拒否は理由と次の行動を欄の誤りで示す（Bolt 39c2 の開始準備の決定 (2)）。 */
    private String respond(
            IngestionOutcome outcome,
            IngestionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        return switch (outcome) {
            case IngestionOutcome.Ingested ingested -> {
                redirectAttributes.addFlashAttribute("result", "取り込みました。");
                yield "redirect:/staff/external-data/ingestions/" + ingested.ingestionId();
            }
            case IngestionOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            case IngestionOutcome.DataSourceNotFound _ ->
                sourceRefused(form, bindingResult, "dataSourceId.notFound", SOURCE_NOT_FOUND, model);
            case IngestionOutcome.DataSourceNotApproved _ ->
                sourceRefused(form, bindingResult, "dataSourceId.notApproved", SOURCE_NOT_APPROVED, model);
            case IngestionOutcome.FileTooLarge _ -> {
                bindingResult.rejectValue(FILE, "file.tooLarge", FILE_TOO_LARGE);
                yield rejected(form, model);
            }
        };
    }

    /** 結果の画面。ない取込 ID と形の誤った ID は共通の 404。 */
    @GetMapping("/{ingestionId}")
    public String result(@PathVariable String ingestionId, Model model) {
        IngestionSummaryView summary = parse(ingestionId)
                .flatMap(summaries::summaryOf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("source", sourceLabel(summary.provider(), summary.recordType()));
        model.addAttribute("ingestedAt", DateTimeDisplay.staff(summary.ingestedAt()));
        model.addAttribute("summary", summary);
        return RESULT_VIEW;
    }

    private String form(IngestionForm form, Model model) {
        model.addAttribute(FORM, form);
        model.addAttribute(
                "sources",
                dataSources.ingestibleSources().stream()
                        .map(source ->
                                new Option(source.id().toString(), sourceLabel(source.provider(), source.recordType())))
                        .toList());
        model.addAttribute("fieldLabels", FIELD_LABELS);
        model.addAttribute("noIngestibleSource", NO_INGESTIBLE_SOURCE);
        model.addAttribute("fileHint", FILE_HINT);
        return NEW_VIEW;
    }

    /**
     * 情報源の拒否（ない・承認されていない）を情報源の欄の誤りにする。選んだ情報源は戻さない（開き直した選択肢にない値は戻せないので、
     * 選び直させる。Bolt 39c2 の設計の決定「拒否の文言」）。
     */
    private String sourceRefused(
            IngestionForm form, BindingResult bindingResult, String code, String message, Model model) {
        form.setDataSourceId(null);
        bindingResult.rejectValue(DATA_SOURCE_ID, code, message);
        return rejected(form, model);
    }

    /** 誤りのある選ぶ画面。ファイルはブラウザの決まりで画面に戻せないので、選んでいたら選び直しを求める。 */
    private String rejected(IngestionForm form, Model model) {
        model.addAttribute("reselectFile", fileChosen(form.getFile()));
        return form(form, model);
    }

    /** ブラウザはファイルを選ばないときも名前の空の部分を送るので、名前の有無で見る（名前のある 0 バイトのファイルは受ける）。 */
    private static boolean fileChosen(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getOriginalFilename();
        return name != null && !name.isBlank();
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<UUID> parse(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /** 原本種別（{@code data_source.record_type}）を取込ファイルの種別に変える。知らない原本種別は空。 */
    private static Optional<CsvFileKind> kindOf(String recordType) {
        return Arrays.stream(CsvFileKind.values())
                .filter(kind -> kind.name().equals(recordType))
                .findFirst();
    }

    /** 情報源の名前「提供元（原本種別の名前）」（Bolt 39c 計画の開始準備の決定 (4)）。知らない原本種別はそのまま示す。 */
    private static String sourceLabel(String provider, String recordType) {
        return provider + "（" + kindOf(recordType).map(KIND_LABELS::get).orElse(recordType) + "）";
    }

    /**
     * 選択肢の 1 つ。
     *
     * @param value 送る値（情報源 ID）
     * @param label 表示名
     */
    record Option(String value, String label) {}
}
