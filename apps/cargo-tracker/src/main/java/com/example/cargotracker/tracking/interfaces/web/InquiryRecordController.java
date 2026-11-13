package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RecordInquiryCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.InquiryRecordingService;
import com.example.cargotracker.tracking.application.internal.commandservices.RecordInquiryOutcome;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-23 照会の記録（カスタマーサポート。US-11 AC2、IR-INV-02。Bolt 40-15）。電話・メールで受けた照会を記録し、必要なら有人案件へ引き継ぐ。
 * 業務の判定（担当範囲・必須項目・重複・期限）はサービスが行い、画面は結果を文と行き先に変えるだけ。記録したら PRG で S-23 に戻って結果を示す。
 */
@Controller
@RequestMapping("/staff/inquiries")
public class InquiryRecordController {

    private static final String FORM_VIEW = "tracking/staff/inquiries/new";
    private static final String RESULT = "result";
    private static final String RESULT_LINK_HREF = "resultLinkHref";
    private static final String RESULT_LINK_LABEL = "resultLinkLabel";
    private static final String PROBLEM = "problem";

    /** エラー要約に出す項目の名前。文言は項目の名前で始めない（共通部品「エラー要約」）。 */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "channel", "経路",
            "trackingNumber", "追跡番号",
            "inquirerKind", "照会者の種別",
            "receivedAt", "受付時刻",
            "content", "照会内容",
            "sourceReference", "参照した出典",
            "department", "担当部門");

    private final InquiryRecordingService service;
    private final Clock clock;

    public InquiryRecordController(InquiryRecordingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** 照会の記録の画面を開く。 */
    @GetMapping("/new")
    public String form(Model model) {
        return formView(new InquiryForm(), model);
    }

    /**
     * 照会を記録する。引き継ぐ指定があれば有人案件へ引き継ぎ、受付番号と期限を示す。追跡番号の追跡記録がない・担当企業の範囲の外のときは同じ文
     * （存在を明かさない）。権限のない役割は 403。
     */
    @PostMapping
    public String record(
            @ModelAttribute("inquiryForm") InquiryForm form,
            BindingResult bindingResult,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirect) {
        Optional<RecordInquiryCommand> command =
                InquiryFormConverter.convert(form, new UtcInstant(clock.instant()), bindingResult);
        if (command.isEmpty()) {
            return formView(form, model);
        }
        switch (service.record(actor, command.get())) {
            case RecordInquiryOutcome.Recorded recorded -> {
                recorded(recorded, redirect);
                return "redirect:/staff/inquiries/new";
            }
            case RecordInquiryOutcome.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            case RecordInquiryOutcome.TrackingNotFound _ ->
                bindingResult.rejectValue("trackingNumber", "trackingNumber.notFound", "この追跡番号の追跡記録は見つかりません");
            case RecordInquiryOutcome.Incomplete(var missing) -> incomplete(missing, bindingResult);
            case RecordInquiryOutcome.CalendarNotRegistered _ ->
                model.addAttribute(PROBLEM, "営業カレンダーが登録されていないため、有人案件へ引き継げません。システムの運用担当に連絡してください。");
        }
        return formView(form, model);
    }

    private static void recorded(RecordInquiryOutcome.Recorded recorded, RedirectAttributes redirect) {
        if (recorded.caseNumber().isEmpty()) {
            redirect.addFlashAttribute(RESULT, "照会を記録しました。");
            return;
        }
        CaseNumber number = recorded.caseNumber().get();
        if (recorded.existingCase()) {
            redirect.addFlashAttribute(RESULT, "照会を記録しました。未終結の同じ内容の有人案件 " + number.value() + " があるため、その案件に引き継ぎました。");
        } else {
            redirect.addFlashAttribute(
                    RESULT,
                    "照会を記録し、有人案件 " + number.value() + " として引き継ぎました。回答期限は "
                            + recorded.answerDueAt().map(ServiceCaseViews::time).orElse("")
                            + "、次回更新は "
                            + recorded.nextUpdateAt()
                                    .map(ServiceCaseViews::time)
                                    .orElse("")
                            + " です。");
        }
        redirect.addFlashAttribute(RESULT_LINK_HREF, "/staff/service-cases/" + number.value());
        redirect.addFlashAttribute(RESULT_LINK_LABEL, "有人案件 " + number.value() + " を開く");
    }

    /** 引き継ぐのに足りない項目を、入力の項目の誤りにする（照会内容と担当部門だけが入力にある）。 */
    private static void incomplete(java.util.Set<MissingItem> missing, BindingResult errors) {
        if (missing.contains(MissingItem.CAUSE)) {
            errors.rejectValue("content", "content.required", "引き継ぐときは入力してください");
        }
        if (missing.contains(MissingItem.DEPARTMENT)) {
            errors.rejectValue("department", "department.required", REQUIRED);
        }
    }

    private static final String REQUIRED = "入力してください";

    private String formView(InquiryForm form, Model model) {
        model.addAttribute("inquiryForm", form);
        model.addAttribute(
                "channels", options(InquiryFormConverter.SELECTABLE_CHANNELS, InquiryRecordController::channel));
        model.addAttribute(
                "inquirerKinds",
                options(List.of(InquirerKind.SHIPPER, InquirerKind.CONSIGNEE), InquiryRecordController::inquirerKind));
        model.addAttribute("fieldLabels", FIELD_LABELS);
        return FORM_VIEW;
    }

    record Option(String value, String label) {}

    private static <E extends Enum<E>> List<Option> options(
            List<E> values, java.util.function.Function<E, String> label) {
        return values.stream()
                .map(value -> new Option(value.name(), label.apply(value)))
                .toList();
    }

    private static String channel(InquiryChannel channel) {
        return switch (channel) {
            case PHONE -> "電話";
            case EMAIL -> "メール";
            case WEB_SELF -> "顧客 Web の自己照会";
            case WEB_INQUIRY -> "顧客 Web の問い合わせ";
        };
    }

    private static String inquirerKind(InquirerKind kind) {
        return switch (kind) {
            case SHIPPER -> "荷主";
            case CONSIGNEE -> "荷受人";
        };
    }
}
