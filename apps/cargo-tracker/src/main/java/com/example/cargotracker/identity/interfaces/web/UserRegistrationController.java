package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationCheck;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationService;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RegistrableCompany;
import com.example.cargotracker.identity.interfaces.web.UserRegistrationPage.FieldError;
import com.example.cargotracker.identity.interfaces.web.UserRegistrationPage.Input;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-20 の利用者の登録（Bolt 38b3b。US-16 AC1・AC2）。一覧の「利用者を登録する」から、企業を選ぶ画面と入力の画面の 2 段（JS なし）で入れ、
 * 確認の領域を経て登録する（PRG で登録した利用者の詳細へ）。メールアドレスと表示名は URL に載せない（確認と戻るは POST と hidden）。確認と
 * 戻るは何も書かず、範囲外も記録しない。範囲外の企業への確定の試行は、値を解釈する前に登録のサービスが記録する。画面の組み立てと文言は
 * {@link UserRegistrationPage} が受け持つ。
 */
@Controller
public class UserRegistrationController {

    /** 企業を選ぶ画面と入力の画面の URL。 */
    static final String NEW_URL = UserAdministrationController.USERS_URL + "/new";

    /** 確認の領域の URL（POST。何も書かない）。 */
    static final String CONFIRMATION_URL = NEW_URL + "/confirmation";

    /** 確認から戻る入力の画面の URL（POST。入力を戻す）。 */
    static final String FORM_URL = NEW_URL + "/form";

    /** 登録の完了の知らせ（UI 設計 S-20）。 */
    static final String REGISTERED_NOTICE = "利用者を登録しました。登録したメールアドレスへ初回の設定の案内を送ります。";

    /** 企業を選ぶ画面から次へ進んだ印（ラジオを選ばずに送ると企業の値が送られないので、初めて開いたときと見分ける）。 */
    private static final String COMPANY_STEP = "company";

    private final UserRegistrationService registrations;
    private final UserRegistrationPage page;

    UserRegistrationController(UserRegistrationService registrations, UserRegistrationPage page) {
        this.registrations = registrations;
        this.page = page;
    }

    @GetMapping(NEW_URL)
    public String start(
            @RequestParam(name = "step", required = false) String step,
            @RequestParam(name = "companyId", required = false) String companyId,
            AuthenticatedActor actor,
            Model model) {
        if (companyId == null || companyId.isBlank()) {
            return page.companies(actor, COMPANY_STEP.equals(step) ? UserRegistrationPage.SELECT_COMPANY : null, model);
        }
        CompanyId id = UserAdministrationParameters.companyId(companyId);
        return page.company(actor, id)
                .map(company -> page.form(company, Input.empty(id), List.of(), model))
                .orElseGet(() -> unavailable(actor, model));
    }

    @PostMapping(CONFIRMATION_URL)
    public String confirm(@ModelAttribute RegistrationForm form, AuthenticatedActor actor, Model model) {
        Input input = form.input();
        return page.company(actor, input.companyId())
                .map(company -> confirmWith(company, input, actor, model))
                .orElseGet(() -> unavailable(actor, model));
    }

    @PostMapping(FORM_URL)
    public String back(@ModelAttribute RegistrationForm form, AuthenticatedActor actor, Model model) {
        Input input = form.input();
        return page.company(actor, input.companyId())
                .map(company -> page.form(company, input, List.of(), model))
                .orElseGet(() -> unavailable(actor, model));
    }

    @PostMapping(UserAdministrationController.USERS_URL)
    public String register(
            @ModelAttribute RegistrationForm form,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 範囲外の企業への試行は、入力の誤りと役割の名前を解釈する前に記録してから 404 にする（開発レビューの高-1）
        if (registrations.denyOutOfScope(actor, form.company())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Input input = form.input();
        List<FieldError> errors = page.inputErrors(input);
        if (!errors.isEmpty()) {
            return withCompany(input, actor, model, _ -> errors);
        }
        return switch (registrations.register(
                actor, input.companyId(), EmailAddress.of(input.email()), input.displayName(), input.roles())) {
            case UserRegistrationOutcome.Registered(var userId) -> {
                redirectAttributes.addFlashAttribute("notice", REGISTERED_NOTICE);
                yield "redirect:" + UserAdministrationController.USERS_URL + "/" + userId.value();
            }
            case UserRegistrationOutcome.Refused(var refusal) -> refused(input, refusal, actor, model);
        };
    }

    private String confirmWith(RegistrableCompany company, Input input, AuthenticatedActor actor, Model model) {
        List<FieldError> errors = page.inputErrors(input);
        if (!errors.isEmpty()) {
            return page.form(company, input, errors, model);
        }
        return switch (registrations.check(
                actor, company.id(), EmailAddress.of(input.email()), input.displayName(), input.roles())) {
            case UserRegistrationCheck.Acceptable _ -> page.confirmation(company, input, actor, model);
            case UserRegistrationCheck.Refused(var refusal) -> refused(input, refusal, actor, model);
        };
    }

    /** 受け付けなかった結果の画面。範囲外とない企業は 404、権限なしは 403、無効な企業は企業を選ぶ画面、ほかは入力の画面に示す。 */
    private String refused(Input input, UserRegistrationRefusal refusal, AuthenticatedActor actor, Model model) {
        return switch (refusal) {
            case UserRegistrationRefusal.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            case UserRegistrationRefusal.CompanyNotFound _, UserRegistrationRefusal.CompanyOutOfScope _ ->
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case UserRegistrationRefusal.InactiveCompany _ -> unavailable(actor, model);
            case UserRegistrationRefusal.InvalidDisplayName _,
                    UserRegistrationRefusal.DuplicateEmail _,
                    UserRegistrationRefusal.Rejected _ ->
                withCompany(
                        input,
                        actor,
                        model,
                        company -> List.of(UserRegistrationPage.refusalError(refusal, company.kind())));
        };
    }

    /** 企業を照会して入力の画面に誤りを示す（企業の種類は役割の拒否の文言に使う）。 */
    private String withCompany(
            Input input,
            AuthenticatedActor actor,
            Model model,
            Function<RegistrableCompany, List<FieldError>> errorsOf) {
        return page.company(actor, input.companyId())
                .map(company -> page.form(company, input, errorsOf.apply(company), model))
                .orElseGet(() -> unavailable(actor, model));
    }

    private String unavailable(AuthenticatedActor actor, Model model) {
        return page.companies(actor, UserRegistrationPage.UNAVAILABLE_COMPANY, model);
    }

    /**
     * 確認・戻る・確定の POST で送られる値（入れたままの文字列。メールアドレスと表示名は URL に載せない）。
     *
     * @param companyId 企業 ID
     * @param email メールアドレス
     * @param displayName 表示名
     * @param roles 付ける役割の名前
     */
    record RegistrationForm(String companyId, String email, String displayName, List<String> roles) {

        RegistrationForm {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }

        /** 送られた企業（ない・形の誤りは 404）。 */
        CompanyId company() {
            if (companyId == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return UserAdministrationParameters.companyId(companyId);
        }

        /** 画面の入力（役割の名前の誤りは 400）。 */
        Input input() {
            return new Input(
                    company(),
                    email == null ? "" : email,
                    displayName == null ? "" : displayName,
                    UserAdministrationParameters.roles(roles));
        }
    }
}
