package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationRefusal;
import com.example.cargotracker.identity.application.internal.queryservices.RegistrableCompanyLookup;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.domain.model.rules.DisplayNameRule;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RegistrableCompany;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.RoleOption;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

/**
 * S-20 の利用者の登録の画面の組み立て（Bolt 38b3b。US-16 AC1・AC2）。企業を選ぶ画面・入力の画面・確認の領域を描き、拒否の文言を決める。
 * 登録は詳細の操作ではないので、詳細の部品 {@link UserDetailPage} を使わず、役割の選択肢の値だけを共有する。範囲外の試行は登録のサービスが
 * 記録し、この部品は記録しない（GET と確認と戻るは記録しない）。合成ルートの {@code @Bean} の戻り値の型の規則（AT-07）に当たらないよう、
 * {@code @Component} で組み立てる。
 */
@Component
final class UserRegistrationPage {

    /** エラー要約の見出し（UI 設計 S-20 の利用者の登録）。 */
    static final String SUMMARY_TITLE = "利用者を登録できませんでした";

    /** 企業を選ばずに次へ進んだときの文言。 */
    static final String SELECT_COMPANY = "登録する企業を 1 つ選んでください。";

    /** 無効・荷受人の企業を選んだときの文言。 */
    static final String UNAVAILABLE_COMPANY = "選んだ企業には利用者を登録できません。ほかの企業を選んでください。";

    private static final String COMPANY_VIEW = "identity/users/registration-company";
    private static final String FORM_VIEW = "identity/users/registration-form";
    private static final String CONFIRMATION_VIEW = "identity/users/registration-confirmation";
    private static final String COMPANY_LABEL = "企業";
    private static final String BLANK = "入力してください。";
    private static final String EMAIL_FORMAT = "名前@ドメイン の形で入力してください（例: user@example.com）。";
    private static final String DUPLICATE_EMAIL = "既に登録されています。ほかのメールアドレスを入力してください。";
    private static final String DISPLAY_NAME_RULE = "見分けやすい文字で、200 文字以内で入力してください。";
    private static final String NO_ROLES = "1 つ以上選んでください。";

    private final UserAdministrationQueryService queryService;

    UserRegistrationPage(UserAdministrationQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 企業を選ぶ画面。誤りがあれば、最初の選択肢へリンクするエラー要約を示す。
     *
     * @param problem 企業の欄の誤りの文言（なければ null）
     */
    String companies(AuthenticatedActor actor, String problem, Model model) {
        List<CompanyOption> options =
                queryService.registrableCompanies(actor).itemsOr(UserDetailPage::forbidden).stream()
                        .map(company -> new CompanyOption(company.id().value().toString(), label(company)))
                        .toList();
        model.addAttribute("companyOptions", options);
        model.addAttribute("companyError", problem);
        model.addAttribute("summary", companySummary(problem, options));
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return COMPANY_VIEW;
    }

    /** 企業の欄の誤りのエラー要約（誤りがなければ null）。リンクは最初の選択肢へ向け、選択肢がなければリンクしない。 */
    private static ConfirmationSummary companySummary(String problem, List<CompanyOption> options) {
        if (problem == null) {
            return null;
        }
        String href =
                options.isEmpty() ? null : "#company-" + options.getFirst().id();
        return new ConfirmationSummary(
                SUMMARY_TITLE, List.of(new ConfirmationSummary.Item(href, COMPANY_LABEL + ": " + problem)));
    }

    /**
     * 登録する企業を照会する。ない・範囲外は 404（記録しない）、システム管理者でなければ 403、無効・荷受人の企業は空（企業を選ぶ画面へ
     * 戻す）。
     */
    Optional<RegistrableCompany> company(AuthenticatedActor actor, CompanyId companyId) {
        return switch (queryService.registrableCompany(actor, companyId)) {
            case RegistrableCompanyLookup.Found(RegistrableCompany found) -> Optional.of(found);
            case RegistrableCompanyLookup.NotFound _, RegistrableCompanyLookup.OutOfScope _ ->
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            case RegistrableCompanyLookup.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            case RegistrableCompanyLookup.Inactive _, RegistrableCompanyLookup.NoAssignableRoles _ -> Optional.empty();
        };
    }

    /** 入力の画面。誤りは欄の順（付ける役割・メールアドレス・表示名）にエラー要約へ並べ、入力を戻す。 */
    String form(RegistrableCompany company, Input input, List<FieldError> errors, Model model) {
        List<RoleOption> roleOptions = Arrays.stream(Role.values())
                .filter(company.assignableRoles()::contains)
                .map(role -> new RoleOption(role, input.roles().contains(role)))
                .toList();
        List<FieldError> ordered =
                errors.stream().sorted(Comparator.comparing(FieldError::field)).toList();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        List<ConfirmationSummary.Item> items = new ArrayList<>();
        for (FieldError error : ordered) {
            fieldErrors.put(error.field().key, error.text());
            items.add(new ConfirmationSummary.Item(
                    anchor(error.field(), roleOptions), error.field().label + ": " + error.text()));
        }
        model.addAttribute("company", company);
        model.addAttribute("companyLabel", label(company));
        model.addAttribute("roleOptions", roleOptions);
        model.addAttribute("email", input.email());
        model.addAttribute("displayName", input.displayName());
        model.addAttribute("errors", fieldErrors);
        model.addAttribute("summary", items.isEmpty() ? null : new ConfirmationSummary(SUMMARY_TITLE, items));
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return FORM_VIEW;
    }

    /** 確認の領域。保存する値（正規化したメールアドレス、前後の空白を除いた表示名）を示し、hidden で持つ。 */
    String confirmation(RegistrableCompany company, Input input, AuthenticatedActor actor, Model model) {
        String email = EmailAddress.of(input.email()).value();
        String displayName = DisplayNameRule.normalize(input.displayName()).orElse(input.displayName());
        model.addAttribute("company", company);
        model.addAttribute("companyLabel", label(company));
        model.addAttribute("email", email);
        model.addAttribute("displayName", displayName);
        model.addAttribute("change", email + "、" + displayName + "、" + UserAdministrationLabels.roles(input.roles()));
        model.addAttribute(
                "selectedRoles",
                UserAdministrationLabels.order(input.roles()).stream()
                        .map(Role::name)
                        .toList());
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return CONFIRMATION_VIEW;
    }

    /** 画面で見る誤り（メールアドレスの空と形、表示名の空、付ける役割のなし）。まとめて示す。 */
    List<FieldError> inputErrors(Input input) {
        List<FieldError> errors = new ArrayList<>();
        if (input.roles().isEmpty()) {
            errors.add(new FieldError(Field.ROLES, NO_ROLES));
        }
        emailError(input.email()).ifPresent(errors::add);
        if (input.displayName() == null || input.displayName().isBlank()) {
            errors.add(new FieldError(Field.DISPLAY_NAME, BLANK));
        }
        return errors;
    }

    /** 登録のサービスの拒否のうち、入力の画面に示す拒否の文言。 */
    static FieldError refusalError(UserRegistrationRefusal refusal, CompanyKind companyKind) {
        return switch (refusal) {
            case UserRegistrationRefusal.InvalidDisplayName _ -> new FieldError(Field.DISPLAY_NAME, DISPLAY_NAME_RULE);
            case UserRegistrationRefusal.DuplicateEmail _ -> new FieldError(Field.EMAIL, DUPLICATE_EMAIL);
            case UserRegistrationRefusal.Rejected(UserRegistrationRejection rejection) ->
                new FieldError(Field.ROLES, roleError(rejection, companyKind));
            case UserRegistrationRefusal.NotPermitted _,
                    UserRegistrationRefusal.CompanyNotFound _,
                    UserRegistrationRefusal.CompanyOutOfScope _,
                    UserRegistrationRefusal.InactiveCompany _ ->
                throw new IllegalArgumentException("入力の画面に示さない拒否: " + refusal);
        };
    }

    private static String roleError(UserRegistrationRejection rejection, CompanyKind companyKind) {
        return switch (rejection.reason()) {
            case NO_ROLES -> NO_ROLES;
            case OVERSIGHT_ROLE -> UserAdministrationLabels.roles(rejection.roles()) + "は、この画面では付けられません。";
            case UNFIT_FOR_COMPANY -> UserAdministrationLabels.unfitRoles(rejection.roles(), companyKind);
            case CONFLICTING_ROLES -> UserAdministrationLabels.conflictingRoles(rejection.roles());
        };
    }

    private static Optional<FieldError> emailError(String email) {
        if (email == null || email.isBlank()) {
            return Optional.of(new FieldError(Field.EMAIL, BLANK));
        }
        try {
            EmailAddress.of(email);
            return Optional.empty();
        } catch (IllegalArgumentException _) {
            return Optional.of(new FieldError(Field.EMAIL, EMAIL_FORMAT));
        }
    }

    /** 誤りの欄へのリンク先（付ける役割は群の最初の選択肢。fieldset はフォーカスを受けない）。 */
    private static String anchor(Field field, List<RoleOption> roleOptions) {
        return switch (field) {
            case ROLES ->
                roleOptions.stream()
                        .findFirst()
                        .map(option -> "#role-" + option.role().name())
                        .orElse(null);
            case EMAIL -> "#email";
            case DISPLAY_NAME -> "#display-name";
        };
    }

    private static String label(RegistrableCompany company) {
        return company.name() + "（" + UserAdministrationLabels.companyKind(company.kind()) + "）";
    }

    /** 入力の画面の欄（宣言の順がエラー要約の順）。 */
    enum Field {
        /** 付ける役割。 */
        ROLES("付ける役割", "roles"),
        /** メールアドレス。 */
        EMAIL("メールアドレス", "email"),
        /** 表示名。 */
        DISPLAY_NAME("表示名", "displayName");

        private final String label;
        private final String key;

        Field(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    /**
     * 送られた入力（確認・戻る・確定の POST の値。メールアドレスと表示名は入れたままの文字列）。
     *
     * @param companyId 企業
     * @param email メールアドレス
     * @param displayName 表示名
     * @param roles 付ける役割
     */
    record Input(CompanyId companyId, String email, String displayName, Set<Role> roles) {

        Input {
            roles = Set.copyOf(roles);
        }

        /** 企業だけを選んだ入力。 */
        static Input empty(CompanyId companyId) {
            return new Input(companyId, "", "", Set.of());
        }
    }

    /**
     * 欄の誤り。
     *
     * @param field 欄
     * @param text 文言（表示名で始めない）
     */
    record FieldError(Field field, String text) {}

    /**
     * 企業の選択肢。
     *
     * @param id 企業 ID
     * @param label 画面の名前（「企業名（企業の種類）」）
     */
    public record CompanyOption(String id, String label) {}
}
