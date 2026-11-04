package com.example.cargotracker.externaldata.interfaces.web;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestibleDataSourceQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryView;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
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
 * いない誤りはサービスを呼ばずに選ぶ画面で示す。拒否（ない情報源・承認されていない情報源・大きすぎるファイル）は 39c では共通の文言で示し、
 * 理由ごとの文言は 39c2 で作る。
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

    /** 拒否の共通の文言（理由ごとの文言は 39c2）。 */
    static final String REFUSED = "取り込めませんでした。情報源とファイルを確かめてください。";

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
            return refused(form, bindingResult, model);
        }
        IngestFileCommand command =
                IngestFileCommandAssembler.assemble(sourceId.get(), kind.get(), bytesOf(form.getFile()));
        return switch (ingestionService.ingest(actor, command)) {
            case IngestionOutcome.Ingested ingested -> {
                redirectAttributes.addFlashAttribute("result", "取り込みました。");
                yield "redirect:/staff/external-data/ingestions/" + ingested.ingestionId();
            }
            case IngestionOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            case IngestionOutcome.DataSourceNotFound _,
                    IngestionOutcome.DataSourceNotApproved _,
                    IngestionOutcome.FileTooLarge _ -> refused(form, bindingResult, model);
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
        return NEW_VIEW;
    }

    private String refused(IngestionForm form, BindingResult bindingResult, Model model) {
        bindingResult.reject("ingestion.refused", REFUSED);
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
        } catch (IllegalArgumentException e) {
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
