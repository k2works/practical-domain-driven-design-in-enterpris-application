package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.AssignmentChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.AssignmentChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserAssignmentService;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignmentChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.ErrorMessage;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Operation;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Retry;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Section;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-20 の担当範囲の変更（Bolt 38e。US-16 AC1）。詳細の担当範囲の領域で担当企業を選んで確認の領域（画面そのものが確認の領域。役割の変更と
 * 同じ）へ進み、確定する（PRG）。判定はサービスが確認の領域を開く時と確定の時の両方で行う。詳細の読み出しと拒否の共通の枝は詳細の部品
 * {@link UserDetailPage} が受け持つ。業務の画面で担当企業が効くのは Bolt 38f・38g から。
 */
@Controller
public class UserAssignmentController {

    /** 担当範囲の変更の確認の URL（GET で確認、POST で確定）。 */
    static final String CONFIRMATION_URL =
            UserAdministrationController.USERS_URL + "/{id}/assignment-changes/confirmation";

    /** 完了の知らせ（UI 設計 S-20）。 */
    static final String CHANGED_NOTICE = "担当範囲を変更しました。";

    private static final String NONE = "なし";

    private final UserAssignmentService assignmentChanges;
    private final UserDetailPage page;

    UserAssignmentController(UserAssignmentService assignmentChanges, UserDetailPage page) {
        this.assignmentChanges = assignmentChanges;
        this.page = page;
    }

    @GetMapping(CONFIRMATION_URL)
    public String confirm(
            @PathVariable String id,
            @RequestParam(name = "companies", required = false) List<String> companies,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = page.find(id, actor, false);
        Set<CompanyId> selected = UserAdministrationParameters.companies(companies);
        return switch (assignmentChanges.check(actor, user.id(), version, selected)) {
            case AssignmentChangeCheck.Acceptable _ -> showConfirmation(user, selected, version, actor, model);
            case AssignmentChangeCheck.Refused(var refusal) ->
                refuse(user.id(), selected, version, actor, model, refusal, false);
        };
    }

    @PostMapping(CONFIRMATION_URL)
    public String change(
            @PathVariable String id,
            @RequestParam(name = "companies", required = false) List<String> companies,
            @RequestParam(name = "version", required = false) String versionValue,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 確定も先に詳細を読み、管理範囲の外なら試行を記録して 404 にする。値は範囲の判定の後に解釈する（役割の変更と同じ）
        UserId target = page.find(id, actor, true).id();
        Set<CompanyId> selected = UserAdministrationParameters.companies(companies);
        long version = UserAdministrationParameters.version(versionValue);
        return switch (assignmentChanges.change(actor, target, version, selected)) {
            case AssignmentChangeOutcome.Changed _ -> {
                redirectAttributes.addFlashAttribute("notice", CHANGED_NOTICE);
                yield "redirect:" + UserAdministrationController.USERS_URL + "/" + target.value();
            }
            case AssignmentChangeOutcome.Refused(var refusal) ->
                refuse(target, selected, version, actor, model, refusal, true);
        };
    }

    /** 受け付けなかった結果の画面。規則の拒否は選んだまま示す（ほかの枝は詳細の部品）。 */
    private String refuse(
            UserId target,
            Set<CompanyId> selected,
            long version,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<AssignmentChangeRejection> refusal,
            boolean recordDenial) {
        return page.refuse(
                target,
                actor,
                model,
                refusal,
                recordDenial,
                new Operation<>(
                        Section.ASSIGNMENTS,
                        (user, rejection) -> new Retry(
                                user.roles(),
                                Optional.empty(),
                                version,
                                errorMessage(rejection),
                                Optional.of(selected))));
    }

    private String showConfirmation(
            UserDetail user, Set<CompanyId> selected, long version, AuthenticatedActor actor, Model model) {
        List<Company> assignable = page.assignableCompanies(actor);
        Set<CompanyId> current = user.assignedCompanies().companies();
        model.addAttribute("user", user);
        model.addAttribute(
                "currentCompanies",
                currentState(assignable, current, UserDetailPage.unchangeableCount(user, assignable)));
        model.addAttribute(
                "addedCompanies",
                names(assignable, company -> selected.contains(company) && !current.contains(company)));
        model.addAttribute(
                "removedCompanies",
                names(assignable, company -> current.contains(company) && !selected.contains(company)));
        model.addAttribute(
                "selectedCompanies",
                assignable.stream()
                        .map(Company::id)
                        .filter(selected::contains)
                        .map(company -> company.value().toString())
                        .toList());
        model.addAttribute(UserAdministrationLabels.VERSION_ATTRIBUTE, version);
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return "identity/users/assignment-confirmation";
    }

    /** 現在の状態の文。選べる企業の名前（リポジトリの順）と、この画面で変えられない担当企業の数。 */
    private static String currentState(List<Company> assignable, Set<CompanyId> current, int unchangeable) {
        return "担当企業: " + names(assignable, current::contains) + UserDetailPage.unchangeableSuffix(unchangeable);
    }

    /** 条件に合う企業の名前を「、」でつなぐ（リポジトリの順）。なければ「なし」。 */
    private static String names(List<Company> assignable, Predicate<CompanyId> filter) {
        List<String> names = assignable.stream()
                .filter(company -> filter.test(company.id()))
                .map(Company::name)
                .toList();
        return names.isEmpty() ? NONE : String.join("、", names);
    }

    /** 担当範囲の変更の拒否の文言（UI 設計 S-20 の表）。担当範囲のない役割の拒否は選び方の誤りではない。 */
    static ErrorMessage errorMessage(AssignmentChangeRejection rejection) {
        return switch (rejection) {
            case NOT_ASSIGNMENT_SCOPED -> new ErrorMessage("この利用者の役割には、担当範囲がありません。", false);
            case UNSELECTABLE_COMPANY -> new ErrorMessage("選べない企業が含まれています。詳細を開き直してください。", true);
            case NO_CHANGE -> new ErrorMessage("今の割り当てから変わっていません。", true);
        };
    }
}
