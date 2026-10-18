package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserRoleChangeService;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.ErrorMessage;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Operation;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Retry;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Section;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-20 の役割の変更（Bolt 34、US-16 の一部。Bolt 34f で一覧・詳細の controller から分けた）。詳細で役割を選んで確認の領域（画面そのものが
 * 確認の領域。S-07・S-09 と同じ）へ進み、確定する（PRG）。判定はサービスが確認の領域を開く時と確定の時の両方で行う。詳細の読み出しと
 * 拒否の共通の枝は詳細の部品 {@link UserDetailPage} が受け持つ。
 */
@Controller
public class UserRoleChangeController {

    /** 役割の変更の確認の URL（GET で確認、POST で確定）。 */
    static final String CONFIRMATION_URL = UserAdministrationController.USERS_URL + "/{id}/role-changes/confirmation";

    /** 完了の知らせ（UI 設計 S-20）。 */
    static final String CHANGED_NOTICE = "役割を変更しました。相手の次の操作から効きます。";

    private final UserRoleChangeService roleChanges;
    private final UserDetailPage page;

    UserRoleChangeController(UserRoleChangeService roleChanges, UserDetailPage page) {
        this.roleChanges = roleChanges;
        this.page = page;
    }

    @GetMapping(CONFIRMATION_URL)
    public String confirm(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = page.find(id, actor, false);
        Set<Role> selected = UserAdministrationParameters.roles(roles);
        return switch (roleChanges.check(actor, user.id(), version, selected)) {
            case RoleChangeCheck.Acceptable _ -> showConfirmation(user, selected, version, actor, model);
            case RoleChangeCheck.Refused(var refusal) ->
                refuse(user.id(), selected, version, actor, model, refusal, false);
        };
    }

    @PostMapping(CONFIRMATION_URL)
    public String change(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 確定も先に詳細を読み、管理範囲の外なら試行を記録して 404 にする（Bolt 34b。書き手は詳細の部品で、確定のときだけ）
        UserId target = page.find(id, actor, true).id();
        Set<Role> selected = UserAdministrationParameters.roles(roles);
        return switch (roleChanges.change(actor, target, version, selected)) {
            case RoleChangeOutcome.Changed _ -> {
                redirectAttributes.addFlashAttribute("notice", CHANGED_NOTICE);
                yield "redirect:" + UserAdministrationController.USERS_URL + "/" + target.value();
            }
            case RoleChangeOutcome.Refused(var refusal) ->
                refuse(target, selected, version, actor, model, refusal, true);
        };
    }

    /** 受け付けなかった結果の画面。規則の拒否は選んだまま示す（ほかの枝は詳細の部品）。 */
    private String refuse(
            UserId target,
            Set<Role> selected,
            long version,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<RoleChangeRejection> refusal,
            boolean recordDenial) {
        return page.refuse(
                target,
                actor,
                model,
                refusal,
                recordDenial,
                new Operation<>(
                        Section.ROLES,
                        (user, rejection) -> new Retry(
                                selected, Optional.empty(), version, errorMessage(rejection, user.companyKind()))));
    }

    private String showConfirmation(
            UserDetail user, Set<Role> selected, long version, AuthenticatedActor actor, Model model) {
        Set<Role> added = EnumSet.noneOf(Role.class);
        added.addAll(selected);
        added.removeAll(user.roles());
        Set<Role> removed = EnumSet.noneOf(Role.class);
        removed.addAll(user.roles());
        removed.removeAll(selected);
        model.addAttribute("user", user);
        model.addAttribute("currentRoles", UserAdministrationLabels.roles(user.roles()));
        model.addAttribute("addedRoles", UserAdministrationLabels.roles(added));
        model.addAttribute("removedRoles", UserAdministrationLabels.roles(removed));
        model.addAttribute(
                "selectedRoles",
                UserAdministrationLabels.order(selected).stream()
                        .map(Role::name)
                        .toList());
        model.addAttribute(UserAdministrationLabels.VERSION_ATTRIBUTE, version);
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return "identity/users/confirmation";
    }

    /**
     * 役割の変更の拒否の文言（UI 設計 S-20 の表）。役割の名前は {@link Role} の宣言の順に「・」でつなぐ。自分の役割の拒否は選び方の誤り
     * ではない。
     */
    static ErrorMessage errorMessage(RoleChangeRejection rejection, CompanyKind companyKind) {
        String text =
                switch (rejection.reason()) {
                    case SELF_CHANGE -> UserDetailPage.SELF_ROLE_NOTE;
                    case NO_ROLES -> "役割を 1 つ以上選んでください。";
                    case NO_CHANGE -> "今の役割から変わっていません。足す役割か外す役割を選んでください。";
                    case OVERSIGHT_ROLE -> "システム管理者・監査担当者の役割は、この画面では付けたり外したりできません。";
                    case UNFIT_FOR_COMPANY ->
                        UserAdministrationLabels.roles(rejection.roles()) + "は、"
                                + UserAdministrationLabels.companyKind(companyKind) + "の利用者には付けられません。";
                    case CONFLICTING_ROLES ->
                        "システム管理者・監査担当者と、業務データを変更できる役割（"
                                + UserAdministrationLabels.roles(
                                        RoleAssignmentPolicy.businessRolesOf(rejection.roles()))
                                + "）は併せて付けられません。";
                };
        return new ErrorMessage(text, rejection.reason() != RoleChangeRejectionReason.SELF_CHANGE);
    }
}
