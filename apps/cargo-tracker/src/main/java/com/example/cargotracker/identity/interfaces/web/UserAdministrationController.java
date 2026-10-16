package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeResult;
import com.example.cargotracker.identity.application.internal.commandservices.UserRoleChangeService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
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
 * S-07・S-09 と同じ）へ進み、確定する（PRG）。判定はサービスが確認の領域を開く時と確定の時の両方で行う。画面で付け外しできる
 * 役割（企業の種類に合う業務の役割）は、表示のためにドメインの規則から読む（判定はサーバーが正）。
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

    /** レイアウトがナビの現在の項目を読むモデルの属性の名前。 */
    private static final String ACTIVE_ATTRIBUTE = "active";

    /** レイアウトのナビの現在の項目。 */
    private static final String ACTIVE = "users";

    private static final String DETAIL_VIEW = "identity/users/detail";

    private final UserAdministrationQueryService queryService;
    private final UserRoleChangeService roleChanges;
    private final AdministrationScopeDenialService denials;

    public UserAdministrationController(
            UserAdministrationQueryService queryService,
            UserRoleChangeService roleChanges,
            AdministrationScopeDenialService denials) {
        this.queryService = queryService;
        this.roleChanges = roleChanges;
        this.denials = denials;
    }

    @GetMapping(USERS_URL)
    public String list(AuthenticatedActor actor, Model model) {
        model.addAttribute("summaries", queryService.list(actor));
        model.addAttribute(ACTIVE_ATTRIBUTE, ACTIVE);
        return "identity/users/list";
    }

    /** 詳細。確認の領域の「戻る」は選んだ役割と画面の版を渡し、選んだまま、確認の前の版のまま示す。 */
    @GetMapping(USERS_URL + "/{id}")
    public String show(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam(name = "version", required = false) Long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = detail(id, actor);
        Set<Role> selected = roles == null ? user.roles() : parseRoles(roles);
        long formVersion = version == null ? user.version() : version;
        return showDetail(new DetailForm(user, selected, formVersion, null), actor, model);
    }

    @GetMapping(CONFIRMATION_URL)
    public String confirm(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = detail(id, actor);
        Set<Role> selected = parseRoles(roles);
        RoleChangeResult result = roleChanges.check(actor, user.id(), version, selected);
        if (result instanceof RoleChangeResult.Acceptable) {
            return showConfirmation(user, selected, version, actor, model);
        }
        return showRefusal(id, selected, version, actor, model, result);
    }

    @PostMapping(CONFIRMATION_URL)
    public String change(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 確定も先に詳細を読み、管理範囲の外なら試行を記録して 404 にする（Bolt 34b。書き手はこの controller だけ）
        UserId target = detail(id, actor).id();
        Set<Role> selected = parseRoles(roles);
        RoleChangeResult result = roleChanges.change(actor, target, version, selected);
        if (result instanceof RoleChangeResult.Changed) {
            redirectAttributes.addFlashAttribute("notice", CHANGED_NOTICE);
            return "redirect:" + USERS_URL + "/" + target.value();
        }
        return showRefusal(id, selected, version, actor, model, result);
    }

    /** 受け付けなかった結果の画面。拒否は選んだまま、古い版は最新の内容で詳細を示し直す。 */
    private String showRefusal(
            String id,
            Set<Role> selected,
            long version,
            AuthenticatedActor actor,
            Model model,
            RoleChangeResult result) {
        return switch (result) {
            case RoleChangeResult.Rejected(RoleChangeRejection rejection) -> {
                UserDetail user = detail(id, actor);
                yield showDetail(
                        new DetailForm(user, selected, version, ErrorMessage.of(rejection, user.companyKind())),
                        actor,
                        model);
            }
            case RoleChangeResult.Stale _ -> {
                UserDetail user = detail(id, actor);
                yield showDetail(
                        new DetailForm(user, user.roles(), user.version(), new ErrorMessage(STALE_MESSAGE, false)),
                        actor,
                        model);
            }
            case RoleChangeResult.NotFound _ -> throw notFound();
            case RoleChangeResult.OutOfScope _ -> {
                // 詳細を読んだ後に管理範囲から外れたとき（同じ request の中の守り）
                denials.recordDenied(actor, parse(id).orElseThrow(UserAdministrationController::notFound));
                throw notFound();
            }
            case RoleChangeResult.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            case RoleChangeResult.Changed _, RoleChangeResult.Acceptable _ ->
                throw new IllegalStateException("受け付けた結果は示し直さない");
        };
    }

    private String showDetail(DetailForm form, AuthenticatedActor actor, Model model) {
        UserDetail user = form.user();
        Set<Role> assignable = RoleAssignmentPolicy.assignableRoles(user.companyKind());
        model.addAttribute("user", user);
        model.addAttribute("currentRoles", names(user.roles()));
        model.addAttribute("companyKindName", companyKindName(user.companyKind()));
        model.addAttribute("self", user.id().equals(actor.userId()));
        model.addAttribute("assignable", !assignable.isEmpty());
        model.addAttribute("roleOptions", roleOptions(assignable, form.selected()));
        // 画面で付け外しできない今の役割（監督の役割など）は、変えずに送るため隠し項目で持つ
        model.addAttribute(
                "keptRoles",
                order(user.roles()).stream()
                        .filter(role -> !assignable.contains(role))
                        .map(Role::name)
                        .toList());
        model.addAttribute("keptRoleNames", names(only(user.roles(), assignable, false)));
        model.addAttribute("version", form.version());
        model.addAttribute("error", form.error());
        model.addAttribute(ACTIVE_ATTRIBUTE, ACTIVE);
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
        model.addAttribute("currentRoles", names(user.roles()));
        model.addAttribute("addedRoles", names(added));
        model.addAttribute("removedRoles", names(removed));
        model.addAttribute(
                "selectedRoles", order(selected).stream().map(Role::name).toList());
        model.addAttribute("version", version);
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(ACTIVE_ATTRIBUTE, ACTIVE);
        return "identity/users/confirmation";
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

    /**
     * 詳細を読む。いなければ 404、管理範囲の外なら試行を記録して同じ 404（存在を示さない。Bolt 34b の決定 3）、システム管理者でなければ
     * 403（役割の変更の権限なしと同じ応答）。
     */
    private UserDetail detail(String id, AuthenticatedActor actor) {
        UserId target = parse(id).orElseThrow(UserAdministrationController::notFound);
        return switch (queryService.detail(actor, target)) {
            case UserLookup.Found(UserDetail found) -> found;
            case UserLookup.NotFound _ -> throw notFound();
            case UserLookup.OutOfScope _ -> {
                denials.recordDenied(actor, target);
                throw notFound();
            }
            case UserLookup.NotPermitted _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    private static Optional<UserId> parse(String id) {
        try {
            return Optional.of(new UserId(UUID.fromString(id)));
        } catch (IllegalArgumentException _) {
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
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private static Set<Role> only(Set<Role> roles, Set<Role> kinds, boolean in) {
        Set<Role> result = EnumSet.noneOf(Role.class);
        roles.stream().filter(role -> kinds.contains(role) == in).forEach(result::add);
        return result;
    }

    /** 画面で付け外しできる役割（企業の種類に合う業務の役割）の選択肢。宣言の順。 */
    private static List<RoleOption> roleOptions(Set<Role> assignable, Set<Role> selected) {
        return Arrays.stream(Role.values())
                .filter(assignable::contains)
                .map(role -> new RoleOption(role, selected.contains(role)))
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

    /**
     * 役割の選択肢。
     *
     * @param role 役割
     * @param selected 選んでいるか
     */
    public record RoleOption(Role role, boolean selected) {}

    /**
     * 詳細の画面に示す内容。
     *
     * @param user 利用者
     * @param selected 選んでいる役割
     * @param version フォームが持つ版
     * @param error 誤り（なければ null）
     */
    private record DetailForm(UserDetail user, Set<Role> selected, long version, ErrorMessage error) {}

    /**
     * 誤りの文言。
     *
     * @param text 文言
     * @param field 役割の選び方の誤りか（エラー要約から役割の欄へリンクし、欄にも示す。自分の役割・古い版は選び方の誤りではない）
     */
    public record ErrorMessage(String text, boolean field) {

        /** 拒否の文言（UI 設計 S-20 の表）。役割の名前は {@link Role} の宣言の順に「・」でつなぐ。 */
        static ErrorMessage of(RoleChangeRejection rejection, CompanyKind companyKind) {
            String text =
                    switch (rejection.reason()) {
                        case SELF_CHANGE -> "自分の役割は変更できません。ほかのシステム管理者に依頼してください。";
                        case NO_ROLES -> "役割を 1 つ以上選んでください。";
                        case NO_CHANGE -> "今の役割から変わっていません。足す役割か外す役割を選んでください。";
                        case OVERSIGHT_ROLE -> "システム管理者・監査担当者の役割は、この画面では付けたり外したりできません。";
                        case UNFIT_FOR_COMPANY ->
                            names(rejection.roles()) + "は、" + companyKindName(companyKind) + "の利用者には付けられません。";
                        case CONFLICTING_ROLES ->
                            "システム管理者・監査担当者と、業務データを変更できる役割（"
                                    + names(RoleAssignmentPolicy.businessRolesOf(rejection.roles())) + "）は併せて付けられません。";
                    };
            return new ErrorMessage(text, rejection.reason() != RoleChangeRejectionReason.SELF_CHANGE);
        }
    }
}
