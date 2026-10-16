package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeResult;
import com.example.cargotracker.identity.application.internal.commandservices.UserRoleChangeService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 社内業務 Web の利用者の管理（S-20。Bolt 34、US-16 の一部）。システム管理者だけが開ける（`SecurityConfiguration` の
 * `/staff/users/**`。サービスも操作者を確かめる）。一覧と詳細を示し、詳細で役割を選んで確認の領域（画面そのものが確認の領域。
 * S-07・S-09 と同じ）へ進み、確定する（PRG）。判定はサービスが確認の領域を開く時と確定の時の両方で行う。
 */
@Controller
public class UserAdministrationController {

    /** 一覧と詳細の URL。 */
    static final String USERS_URL = "/staff/users";

    /** 役割の変更の確認の URL（GET で確認、POST で確定）。 */
    static final String CONFIRMATION_URL = USERS_URL + "/{id}/role-changes/confirmation";

    /** 完了の知らせ（UI 設計 S-20）。 */
    static final String CHANGED_NOTICE = "役割を変更しました。相手の次の操作から効きます。";

    /** 先に更新されていたときの文言（UI 設計 S-20。誰が変えたかは言わない）。 */
    static final String STALE_MESSAGE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

    /** レイアウトのナビの現在の項目。 */
    private static final String ACTIVE = "users";

    private static final String DETAIL_VIEW = "identity/users/detail";

    private final UserAdministrationQueryService queryService;
    private final UserRoleChangeService roleChanges;

    public UserAdministrationController(
            UserAdministrationQueryService queryService, UserRoleChangeService roleChanges) {
        this.queryService = queryService;
        this.roleChanges = roleChanges;
    }

    @GetMapping(USERS_URL)
    public String list(Model model) {
        model.addAttribute("users", queryService.list());
        model.addAttribute("active", ACTIVE);
        return "identity/users/list";
    }

    /** 詳細。確認の領域の「戻る」は選んだ役割を {@code roles} で渡し、選んだまま示す。 */
    @GetMapping(USERS_URL + "/{id}")
    public String show(
            @PathVariable String id, @RequestParam(name = "roles", required = false) List<String> roles, Model model) {
        UserDetail user = detail(id);
        return showDetail(user, roles == null ? user.roles() : parseRoles(roles), null, model);
    }

    @GetMapping(CONFIRMATION_URL)
    public String confirm(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = detail(id);
        Set<Role> selected = parseRoles(roles);
        return switch (roleChanges.check(actor, user.id(), version, selected)) {
            case RoleChangeResult.Acceptable acceptable -> showConfirmation(user, selected, version, actor, model);
            case RoleChangeResult.Rejected rejected ->
                showDetail(user, selected, rejectionMessage(rejected.rejection(), user.companyKind()), model);
            case RoleChangeResult.Stale stale -> showDetail(user, user.roles(), STALE_MESSAGE, model);
            case RoleChangeResult.NotFound notFound -> throw notFound();
            case RoleChangeResult.NotPermitted notPermitted -> throw forbidden();
            case RoleChangeResult.Changed changed -> throw new IllegalStateException("確かめるだけの呼び出しで変更した");
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
        UserId target = parse(id).orElseThrow(UserAdministrationController::notFound);
        Set<Role> selected = parseRoles(roles);
        return switch (roleChanges.change(actor, target, version, selected)) {
            case RoleChangeResult.Changed changed -> {
                redirectAttributes.addFlashAttribute("notice", CHANGED_NOTICE);
                yield "redirect:" + USERS_URL + "/" + target.value();
            }
            case RoleChangeResult.Rejected rejected -> {
                UserDetail user = detail(id);
                yield showDetail(user, selected, rejectionMessage(rejected.rejection(), user.companyKind()), model);
            }
            case RoleChangeResult.Stale stale -> {
                UserDetail user = detail(id);
                yield showDetail(user, user.roles(), STALE_MESSAGE, model);
            }
            case RoleChangeResult.NotFound notFound -> throw notFound();
            case RoleChangeResult.NotPermitted notPermitted -> throw forbidden();
            case RoleChangeResult.Acceptable acceptable -> throw new IllegalStateException("変更の呼び出しで確かめるだけになった");
        };
    }

    private String showDetail(UserDetail user, Set<Role> selected, String error, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("roleOptions", roleOptions(user, selected));
        model.addAttribute("companyKindName", companyKindName(user.companyKind()));
        model.addAttribute("error", error);
        model.addAttribute("active", ACTIVE);
        return DETAIL_VIEW;
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
        model.addAttribute("companyKindName", companyKindName(user.companyKind()));
        model.addAttribute("currentRoles", names(user.roles()));
        model.addAttribute("addedRoles", names(added));
        model.addAttribute("removedRoles", names(removed));
        model.addAttribute(
                "selectedRoles", order(selected).stream().map(Role::name).toList());
        model.addAttribute("version", version);
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute("active", ACTIVE);
        return "identity/users/confirmation";
    }

    /** 拒否の文言（UI 設計 S-20 の表）。役割の名前は {@link Role} の宣言の順に「・」でつなぐ。 */
    static String rejectionMessage(RoleChangeRejection rejection, CompanyKind companyKind) {
        return switch (rejection.reason()) {
            case SELF_CHANGE -> "自分の役割は変更できません。ほかのシステム管理者に依頼してください。";
            case NO_ROLES -> "役割を 1 つ以上選んでください。";
            case NO_CHANGE -> "今の役割から変わっていません。足す役割か外す役割を選んでください。";
            case UNFIT_FOR_COMPANY -> names(rejection.roles()) + "は、" + companyKindName(companyKind) + "の利用者には付けられません。";
            case CONFLICTING_ROLES ->
                "システム管理者・監査担当者と、業務データを変更できる役割（" + names(businessRoles(rejection.roles())) + "）は併せて付けられません。";
        };
    }

    private static Set<Role> businessRoles(Set<Role> roles) {
        Set<Role> business = EnumSet.noneOf(Role.class);
        business.addAll(roles);
        business.removeAll(EnumSet.of(Role.SYSTEM_ADMIN, Role.AUDITOR));
        return business;
    }

    /** 役割の表示名を {@link Role} の宣言の順に「・」でつなぐ。なければ「なし」。 */
    static String names(Set<Role> roles) {
        if (roles.isEmpty()) {
            return "なし";
        }
        return order(roles).stream().map(Role::displayName).collect(Collectors.joining("・"));
    }

    private static List<Role> order(Set<Role> roles) {
        return roles.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(roles));
    }

    private UserDetail detail(String id) {
        return parse(id).flatMap(queryService::detail).orElseThrow(UserAdministrationController::notFound);
    }

    private static Optional<UserId> parse(String id) {
        try {
            return Optional.of(new UserId(UUID.fromString(id)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 送られた役割の名前を役割にする。知らない名前は 400。 */
    private static Set<Role> parseRoles(List<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        try {
            return roles.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** 8 つの役割の選択肢。企業の種類に付けられない役割は、選べない旨を添える（判定はサーバーが正。IA-INV-13）。 */
    private static List<RoleOption> roleOptions(UserDetail user, Set<Role> selected) {
        Set<Role> unfit = RoleAssignmentPolicy.unfitRoles(user.companyKind(), Set.of(Role.values()));
        return Arrays.stream(Role.values())
                .map(role -> new RoleOption(role, selected.contains(role), !unfit.contains(role)))
                .toList();
    }

    /** 企業の種類の画面の名前（UI 設計 S-20）。 */
    static String companyKindName(CompanyKind kind) {
        return switch (kind) {
            case SHIPPER -> "荷主企業";
            case CONSIGNEE -> "荷受人の企業";
            case OPERATOR -> "運用する会社";
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /**
     * 役割の選択肢。
     *
     * @param role 役割
     * @param selected 選んでいるか
     * @param fit 利用者の企業の種類に付けられるか
     */
    public record RoleOption(Role role, boolean selected, boolean fit) {}
}
