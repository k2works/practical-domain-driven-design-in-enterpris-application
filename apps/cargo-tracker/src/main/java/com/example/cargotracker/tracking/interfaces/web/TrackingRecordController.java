package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneRegistrationOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-11 追跡一覧、S-12 追跡の詳細、S-13 主要実績の登録（追跡管理者。Bolt 26・26c）。追跡管理者のホームは S-11。絞り込み
 * （確認中・鮮度超過・訂正承認待ち）は W7 以後、訂正は US-13。
 *
 * <p>S-13 はコマンドサービスの結果を文言と行き先に変えるだけで、業務の判定（同じ出典・未来の発生時刻）を持たない。
 *
 * <p>Bolt 38g（US-16 AC4）から、追跡管理者の担当企業の追跡記録だけを扱う。範囲の外は存在しない追跡記録と同じく 404 にし、マイルストーンの
 * 登録（POST）の範囲の外だけを試行として記録する（{@link TrackingOutOfScopeResponder}。SEC-12、ADR-019）。登録の POST は入力の変換より先に
 * ロックしない照会で範囲を確かめる（束縛の誤りは BindingResult が受けるので、範囲の外なら値によらず 404 と記録）。
 */
@Controller
@RequestMapping("/staff/tracking-records")
public class TrackingRecordController {

    private static final String LIST_VIEW = "tracking/staff/tracking-records/list";
    private static final String DETAIL_VIEW = "tracking/staff/tracking-records/show";
    private static final String MILESTONE_FORM_VIEW = "tracking/staff/milestones/new";
    private static final String RESULT = "result";
    private static final String RESULT_LINK_HREF = "resultLinkHref";
    private static final String RESULT_LINK_LABEL = "resultLinkLabel";
    private static final String PROBLEM = "problem";
    private static final String CONFLICT_MESSAGE = "ほかの追跡管理者が先にこの追跡記録を更新しました。最新の主要実績を確かめ、必要なら、もう一度登録してください。";

    /** エラー要約に出す項目の名前。文言は項目の名前で始めない（共通部品「エラー要約」）。 */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "kind", "種類",
            "location", "場所",
            "occurredAt", "発生時刻",
            "sourceKind", "出典の種類",
            "sourceReference", "出典の参照");

    private final TrackingRecordQueryService queryService;
    private final TrackingRecordCommandService commandService;
    private final Clock clock;
    private final TrackingOutOfScopeResponder outOfScope;

    public TrackingRecordController(
            TrackingRecordQueryService queryService,
            TrackingRecordCommandService commandService,
            Clock clock,
            TrackingOutOfScopeResponder outOfScope) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.clock = clock;
        this.outOfScope = outOfScope;
    }

    /** 追跡一覧（S-11 の最小の表示）。担当企業の追跡記録を追跡の開始時刻の新しい順に上限まで示し、追跡番号から S-12 を開ける。 */
    @GetMapping
    public String list(AuthenticatedActor actor, Model model) {
        RecentTrackingRecords recent = queryService.recent(actor);
        model.addAttribute("noAssignedCompanies", actor.hasNoAssignedCompanies());
        model.addAttribute("trackingRecords", TrackingRecordViews.list(recent));
        model.addAttribute("truncated", recent.truncated());
        model.addAttribute("limit", recent.limit());
        return LIST_VIEW;
    }

    /** 追跡の詳細（S-12）。形式の誤った追跡番号は、ない追跡番号と同じく 404 にする。 */
    @GetMapping("/{trackingNumber}")
    public String detail(@PathVariable String trackingNumber, AuthenticatedActor actor, Model model) {
        model.addAttribute("trackingRecord", TrackingRecordViews.detail(found(trackingNumber, actor)));
        return DETAIL_VIEW;
    }

    /** 主要実績の登録（S-13）。画面を開いたときの追跡記録の版を隠し項目に入れる（競合の検出）。 */
    @GetMapping("/{trackingNumber}/milestones/new")
    public String newMilestone(@PathVariable String trackingNumber, AuthenticatedActor actor, Model model) {
        TrackingRecord trackingRecord = found(trackingNumber, actor);
        MilestoneForm form = new MilestoneForm();
        form.setExpectedVersion(trackingRecord.aggregateVersion());
        return milestoneForm(trackingRecord.trackingNumber(), form, model);
    }

    /**
     * 主要実績を登録する（S-13、US-12 AC1・AC2）。登録したら PRG で S-12 に戻って結果を示す。同じ出典の実績があれば、望んだ結果が
     * 成り立っているので警告でなく結果として示し、既存の実績への導線を文の外に置く（Bolt 24 の前例。WCAG 2.4.4）。
     */
    @PostMapping("/{trackingNumber}/milestones")
    public String registerMilestone(
            @PathVariable String trackingNumber,
            @ModelAttribute("milestoneForm") MilestoneForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        TrackingNumber number =
                TrackingRecordViews.parseTrackingNumber(trackingNumber).orElseThrow(this::notFound);
        // 範囲の判定は入力の変換より先（Bolt 38g の行 4。範囲の外なら値の誤りによらず 404 と記録）
        rejectOutOfScope(number, actor);
        Optional<RegisterMilestoneCommand> converted = MilestoneFormConverter.convert(
                form, number, actor.userId(), new UtcInstant(clock.instant()), bindingResult);
        if (converted.isEmpty()) {
            return milestoneForm(number, form, model);
        }
        RegisterMilestoneCommand command = converted.get();
        switch (commandService.registerMilestone(command, actor)) {
            case MilestoneRegistrationOutcome.Registered(int milestoneNo, TrackingStatus currentStatus) ->
                // 導出し直した現在状態も示す（AC1 の「現在状態が再評価される」を利用者に伝える）
                redirectAttributes.addFlashAttribute(
                        RESULT,
                        "実績 " + milestoneNo + " を登録しました。現在状態は「" + TrackingRecordViews.status(currentStatus) + "」です。");
            case MilestoneRegistrationOutcome.AlreadyRegistered(int milestoneNo) -> {
                String source = TrackingRecordViews.sourceLabel(
                        command.source().kind(), command.source().reference());
                redirectAttributes.addFlashAttribute(
                        RESULT, "出典（" + source + "）の実績はすでに登録されています（実績 " + milestoneNo + "）。今回の入力は登録していません。");
                redirectAttributes.addFlashAttribute(RESULT_LINK_HREF, "#milestone-" + milestoneNo);
                // リンクの名前だけでどの実績か分かるように、実績番号を入れる（WCAG 2.4.4。Bolt 24 レビュー U-1 と同じ）
                redirectAttributes.addFlashAttribute(RESULT_LINK_LABEL, "実績 " + milestoneNo + " を一覧で見る");
            }
            case MilestoneRegistrationOutcome.Conflict _ ->
                redirectAttributes.addFlashAttribute(PROBLEM, CONFLICT_MESSAGE);
            case MilestoneRegistrationOutcome.Rejected(MilestoneRejectionReason reason) -> {
                bindingResult.rejectValue("occurredAt", "occurredAt.rejected", rejection(reason));
                return milestoneForm(number, form, model);
            }
            case MilestoneRegistrationOutcome.NotFound _ -> throw notFound();
            case MilestoneRegistrationOutcome.OutOfScope _ -> throw recordOutOfScope(number, actor);
        }
        // 行き先の追跡番号はパスの変数から展開する（S-12）
        return "redirect:/staff/tracking-records/{trackingNumber}";
    }

    private static String rejection(MilestoneRejectionReason reason) {
        return switch (reason) {
            case OCCURRED_IN_FUTURE -> "いまより前の日時を入力してください";
        };
    }

    private String milestoneForm(TrackingNumber trackingNumber, MilestoneForm form, Model model) {
        model.addAttribute("trackingNumber", trackingNumber.value());
        model.addAttribute("milestoneForm", form);
        model.addAttribute("kinds", options(Arrays.asList(MilestoneKind.values()), TrackingRecordViews::kind));
        model.addAttribute(
                "sourceKinds",
                options(MilestoneFormConverter.SELECTABLE_SOURCE_KINDS, TrackingRecordViews::sourceKind));
        model.addAttribute("fieldLabels", FIELD_LABELS);
        return MILESTONE_FORM_VIEW;
    }

    /**
     * 選択肢の 1 つ。
     *
     * @param value 送る値
     * @param label 表示名
     */
    record Option(String value, String label) {}

    private static <E extends Enum<E>> List<Option> options(List<E> values, Function<E, String> label) {
        return values.stream()
                .map(value -> new Option(value.name(), label.apply(value)))
                .toList();
    }

    private TrackingRecord found(String trackingNumber, AuthenticatedActor actor) {
        return TrackingRecordViews.parseTrackingNumber(trackingNumber)
                .flatMap(number -> queryService.detail(number, actor))
                .orElseThrow(this::notFound);
    }

    /** 状態を変える POST の前の確かめ。範囲の外なら試行を記録して 404（値の解釈より先。Bolt 38g の行 4）。 */
    private void rejectOutOfScope(TrackingNumber number, AuthenticatedActor actor) {
        if (queryService.outOfScope(number, actor)) {
            throw recordOutOfScope(number, actor);
        }
    }

    private ResponseStatusException recordOutOfScope(TrackingNumber number, AuthenticatedActor actor) {
        return outOfScope.recordAndNotFound(actor, number.value());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
