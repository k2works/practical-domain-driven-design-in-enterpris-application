package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.StatusChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.StatusChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserRoleChangeService;
import com.example.cargotracker.identity.application.internal.commandservices.UserStatusChangeService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
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
 * 役割（企業の種類に合う業務の役割）は、表示のためにドメインの規則から読む（判定はサーバーが正）。Bolt 34d で、詳細の利用状態の領域から
 * 理由を選んで利用を停止し、停止中の利用者を再開する確認の領域を足した（US-16 AC5。役割の変更と同じ確認と確定の形）。
 */
@Controller
public class UserAdministrationController {

    /** 一覧と詳細の URL。 */
    static final String USERS_URL = "/staff/users";

    /** 役割の変更の確認の URL（GET で確認、POST で確定）。 */
    static final String CONFIRMATION_URL = USERS_URL + "/{id}/role-changes/confirmation";

    /** 利用状態の変更の確認の URL（GET で確認、POST で確定。Bolt 34d）。 */
    static final String STATUS_CONFIRMATION_URL = USERS_URL + "/{id}/status-changes/confirmation";

    /** 停止の完了の知らせ（UI 設計 S-20。Bolt 34d）。 */
    static final String SUSPENDED_NOTICE = "利用を停止しました。相手の次の操作から使えなくなります。";

    /** 再開の完了の知らせ（UI 設計 S-20。Bolt 34d）。 */
    static final String REACTIVATED_NOTICE = "利用を再開しました。相手は次のログインから使えます。";

    /** 自分の詳細の利用状態の領域の案内（UI 設計 S-20。Bolt 34d）。 */
    static final String SELF_STATUS_NOTE = "自分の利用状態は変更できません。ほかのシステム管理者に依頼してください。";

    /** 監督の役割を持つ利用者の詳細の利用状態の領域の案内（UI 設計 S-20。Bolt 34d）。 */
    static final String OVERSIGHT_STATUS_NOTE = "システム管理者・監査担当者の利用停止と再開は、この画面では行いません。";

    /** 完了の知らせ（UI 設計 S-20）。 */
    static final String CHANGED_NOTICE = "役割を変更しました。相手の次の操作から効きます。";

    /** 先に更新されていたときの文言（UI 設計 S-20。誰が変えたかは言わない）。 */
    static final String STALE_MESSAGE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

    /** フォームが持つ版のモデルの属性の名前。 */
    private static final String VERSION_ATTRIBUTE = "version";

    /** レイアウトがナビの現在の項目を読むモデルの属性の名前。 */
    private static final String ACTIVE_ATTRIBUTE = "active";

    /** レイアウトのナビの現在の項目。 */
    private static final String ACTIVE = "users";

    private static final String DETAIL_VIEW = "identity/users/detail";

    private final UserAdministrationQueryService queryService;
    private final UserRoleChangeService roleChanges;
    private final UserStatusChangeService statusChanges;
    private final AdministrationScopeDenialService denials;

    public UserAdministrationController(
            UserAdministrationQueryService queryService,
            UserRoleChangeService roleChanges,
            UserStatusChangeService statusChanges,
            AdministrationScopeDenialService denials) {
        this.queryService = queryService;
        this.roleChanges = roleChanges;
        this.statusChanges = statusChanges;
        this.denials = denials;
    }

    @GetMapping(USERS_URL)
    public String list(AuthenticatedActor actor, Model model) {
        model.addAttribute("summaries", queryService.list(actor));
        model.addAttribute(ACTIVE_ATTRIBUTE, ACTIVE);
        return "identity/users/list";
    }

    /**
     * 詳細。確認の領域の「戻る」は選んだ役割（または選んだ停止の理由）と画面の版を渡し、選んだまま、確認の前の版のまま示す。
     */
    @GetMapping(USERS_URL + "/{id}")
    public String show(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "version", required = false) Long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = detail(id, actor, false);
        Set<Role> selected = roles == null ? user.roles() : parseRoles(roles);
        long formVersion = version == null ? user.version() : version;
        return showDetail(new DetailForm(user, selected, parseReason(reason), formVersion, null, null), actor, model);
    }

    @GetMapping(CONFIRMATION_URL)
    public String confirm(
            @PathVariable String id,
            @RequestParam(name = "roles", required = false) List<String> roles,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = detail(id, actor, false);
        Set<Role> selected = parseRoles(roles);
        return switch (roleChanges.check(actor, user.id(), version, selected)) {
            case RoleChangeCheck.Acceptable _ -> showConfirmation(user, selected, version, actor, model);
            case RoleChangeCheck.Refused(var refusal) ->
                showRefusal(id, selected, version, actor, model, refusal, false);
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
        // 確定も先に詳細を読み、管理範囲の外なら試行を記録して 404 にする（Bolt 34b。書き手はこの controller の確定だけ）
        UserId target = detail(id, actor, true).id();
        Set<Role> selected = parseRoles(roles);
        return switch (roleChanges.change(actor, target, version, selected)) {
            case RoleChangeOutcome.Changed _ -> {
                redirectAttributes.addFlashAttribute("notice", CHANGED_NOTICE);
                yield "redirect:" + USERS_URL + "/" + target.value();
            }
            case RoleChangeOutcome.Refused(var refusal) ->
                showRefusal(id, selected, version, actor, model, refusal, true);
        };
    }

    /** 受け付けなかった結果の画面。拒否は選んだまま、古い版は最新の内容で詳細を示し直す。 */
    private String showRefusal(
            String id,
            Set<Role> selected,
            long version,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<RoleChangeRejection> refusal,
            boolean change) {
        return switch (refusal) {
            case UserAdministrationRefusal.Rejected(RoleChangeRejection rejection) -> {
                UserDetail user = detail(id, actor, false);
                yield showDetail(
                        new DetailForm(
                                user,
                                selected,
                                Optional.empty(),
                                version,
                                ErrorMessage.of(rejection, user.companyKind()),
                                null),
                        actor,
                        model);
            }
            case UserAdministrationRefusal.Stale<?> _ -> {
                UserDetail user = detail(id, actor, false);
                yield showDetail(
                        new DetailForm(
                                user,
                                user.roles(),
                                Optional.empty(),
                                user.version(),
                                new ErrorMessage(STALE_MESSAGE, false),
                                null),
                        actor,
                        model);
            }
            case UserAdministrationRefusal.NotFound<?> _ -> throw notFound();
            case UserAdministrationRefusal.OutOfScope<?> _ -> {
                // 詳細を読んだ後に管理範囲から外れたとき（同じ request の中の守り）。記録は確定のときだけ
                if (change) {
                    denials.recordDenied(actor, parse(id).orElseThrow(UserAdministrationController::notFound));
                }
                throw notFound();
            }
            case UserAdministrationRefusal.NotPermitted<?> _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    @GetMapping(STATUS_CONFIRMATION_URL)
    public String confirmStatus(
            @PathVariable String id,
            @RequestParam String status,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam long version,
            AuthenticatedActor actor,
            Model model) {
        UserDetail user = detail(id, actor, false);
        StatusRequest request = StatusRequest.parse(status, reason);
        return switch (statusChanges.check(actor, user.id(), version, request.to(), request.reason())) {
            case StatusChangeCheck.Acceptable _ -> showStatusConfirmation(user, request, version, actor, model);
            case StatusChangeCheck.Refused(var refusal) ->
                showStatusRefusal(id, request, version, actor, model, refusal, false);
        };
    }

    /**
     * 利用状態の変更の確定。状態と版の値は、先に詳細を読んで管理範囲を確かめてから解釈する（値の誤りの 400 を範囲の判定より先にすると、
     * 範囲外の利用者への確定の試行が記録に残らない。Bolt 34d の開発レビュー）。
     */
    @PostMapping(STATUS_CONFIRMATION_URL)
    public String changeStatus(
            @PathVariable String id,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "version", required = false) String versionValue,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 役割の変更と同じく、確定も先に詳細を読み、管理範囲の外なら試行を記録して 404 にする（書き手はこの controller の確定だけ）
        UserId target = detail(id, actor, true).id();
        StatusRequest request = StatusRequest.parse(status, reason);
        long version = parseVersion(versionValue);
        return switch (statusChanges.change(actor, target, version, request.to(), request.reason())) {
            case StatusChangeOutcome.Changed _ -> {
                redirectAttributes.addFlashAttribute(
                        "notice", request.to() == UserStatus.SUSPENDED ? SUSPENDED_NOTICE : REACTIVATED_NOTICE);
                yield "redirect:" + USERS_URL + "/" + target.value();
            }
            case StatusChangeOutcome.Refused(var refusal) ->
                showStatusRefusal(id, request, version, actor, model, refusal, true);
        };
    }

    /** 利用状態の変更を受け付けなかった結果の画面。拒否は選んだ理由のまま、古い版は最新の内容で詳細を示し直す。 */
    private String showStatusRefusal(
            String id,
            StatusRequest request,
            long version,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<StatusChangeRejection> refusal,
            boolean change) {
        return switch (refusal) {
            case UserAdministrationRefusal.Rejected(StatusChangeRejection rejection) -> {
                UserDetail user = detail(id, actor, false);
                // 同じ状態の拒否は、ほかの管理者が先に同じ変更を確定したときに起きる。次の操作が古い版で拒まれないよう、最新の版で示す
                long formVersion = rejection == StatusChangeRejection.NO_CHANGE ? user.version() : version;
                yield showDetail(
                        new DetailForm(
                                user,
                                user.roles(),
                                request.reason(),
                                formVersion,
                                null,
                                ErrorMessage.of(rejection, user.status())),
                        actor,
                        model);
            }
            case UserAdministrationRefusal.Stale<?> _ -> {
                UserDetail user = detail(id, actor, false);
                yield showDetail(
                        new DetailForm(
                                user,
                                user.roles(),
                                Optional.empty(),
                                user.version(),
                                null,
                                new ErrorMessage(STALE_MESSAGE, false)),
                        actor,
                        model);
            }
            case UserAdministrationRefusal.NotFound<?> _ -> throw notFound();
            case UserAdministrationRefusal.OutOfScope<?> _ -> {
                // 詳細を読んだ後に管理範囲から外れたとき（同じ request の中の守り）。記録は確定のときだけ
                if (change) {
                    denials.recordDenied(actor, parse(id).orElseThrow(UserAdministrationController::notFound));
                }
                throw notFound();
            }
            case UserAdministrationRefusal.NotPermitted<?> _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        };
    }

    private String showStatusConfirmation(
            UserDetail user, StatusRequest request, long version, AuthenticatedActor actor, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("suspending", request.to() == UserStatus.SUSPENDED);
        model.addAttribute("currentStatus", statusName(user.status()));
        model.addAttribute(
                "reasonName",
                request.reason().map(UserAdministrationController::reasonName).orElse(null));
        model.addAttribute("status", request.to().name());
        model.addAttribute(
                "reason", request.reason().map(SuspensionReason::name).orElse(null));
        model.addAttribute(VERSION_ATTRIBUTE, version);
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(ACTIVE_ATTRIBUTE, ACTIVE);
        return "identity/users/status-confirmation";
    }

    private String showDetail(DetailForm form, AuthenticatedActor actor, Model model) {
        UserDetail user = form.user();
        boolean self = user.id().equals(actor.userId());
        model.addAttribute("statusNote", statusNote(user, self));
        model.addAttribute("reasonOptions", reasonOptions(form.reason()));
        model.addAttribute("statusError", form.statusError());
        model.addAttribute("summary", ErrorSummary.of(form));
        Set<Role> assignable = RoleAssignmentPolicy.assignableRoles(user.companyKind());
        model.addAttribute("user", user);
        model.addAttribute("currentRoles", names(user.roles()));
        model.addAttribute("companyKindName", companyKindName(user.companyKind()));
        model.addAttribute("self", self);
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
        model.addAttribute(VERSION_ATTRIBUTE, form.version());
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
        model.addAttribute(VERSION_ATTRIBUTE, version);
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
     * 詳細を読む。いなければ 404、管理範囲の外なら同じ 404（存在を示さない。Bolt 34b の決定 3）、システム管理者でなければ 403（役割の変更の
     * 権限なしと同じ応答）。範囲外の試行は、確定（{@code recordDenial}）のときだけ記録する（GET は CSRF の守りがないので記録しない。
     * Bolt 34b の開発レビューの決定）。
     */
    private UserDetail detail(String id, AuthenticatedActor actor, boolean recordDenial) {
        UserId target = parse(id).orElseThrow(UserAdministrationController::notFound);
        return switch (queryService.detail(actor, target)) {
            case UserLookup.Found(UserDetail found) -> found;
            case UserLookup.NotFound _ -> throw notFound();
            case UserLookup.OutOfScope _ -> {
                if (recordDenial) {
                    denials.recordDenied(actor, target);
                }
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

    /** 送られた版を数にする。ない・数でなければ 400。 */
    private static long parseVersion(String version) {
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** 送られた停止の理由の名前を理由にする。空や未指定は理由なし（停止なら判定が拒む）、知らない名前は 400。 */
    private static Optional<SuspensionReason> parseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(SuspensionReason.valueOf(reason));
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** 利用状態の領域の案内。自分と監督の役割を持つ利用者には操作を出さず、この一文を示す（Bolt 34d の決定 4）。操作を出すなら null。 */
    private static String statusNote(UserDetail user, boolean self) {
        if (self) {
            return SELF_STATUS_NOTE;
        }
        if (RoleAssignmentPolicy.holdsOversightRole(user.roles())) {
            return OVERSIGHT_STATUS_NOTE;
        }
        return null;
    }

    /** 停止の理由の選択肢。宣言の順。 */
    private static List<ReasonOption> reasonOptions(Optional<SuspensionReason> selected) {
        return Arrays.stream(SuspensionReason.values())
                .map(reason -> new ReasonOption(reason, reasonName(reason), selected.equals(Optional.of(reason))))
                .toList();
    }

    /** 停止の理由の画面の名前（UI 設計 S-20）。 */
    static String reasonName(SuspensionReason reason) {
        return switch (reason) {
            case USAGE_ENDED -> "利用終了";
            case TRANSFERRED -> "異動";
            case EMERGENCY -> "緊急停止";
        };
    }

    /** 利用状態の画面の名前（UI 設計 S-20）。 */
    static String statusName(UserStatus status) {
        return status == UserStatus.ACTIVE ? "利用中" : "利用停止";
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
     * 詳細のエラー要約（Bolt 34d で役割の変更と利用状態の変更の 2 つを 1 つにした）。
     *
     * @param title 見出し
     * @param error 誤り
     * @param anchor 選び方の誤りのときにリンクする欄
     */
    public record ErrorSummary(String title, ErrorMessage error, String anchor) {

        /** 詳細に示す誤りの要約。誤りがなければ null。 */
        static ErrorSummary of(DetailForm form) {
            if (form.error() != null) {
                return new ErrorSummary("役割を変更できませんでした", form.error(), "#roles");
            }
            if (form.statusError() != null) {
                return new ErrorSummary("利用状態を変更できませんでした", form.statusError(), "#suspension-reason");
            }
            return null;
        }
    }

    /**
     * 停止の理由の選択肢。
     *
     * @param reason 理由
     * @param name 画面の名前
     * @param selected 選んでいるか
     */
    public record ReasonOption(SuspensionReason reason, String name, boolean selected) {}

    /**
     * 利用状態の変更の送信（状態と理由の名前を値にしたもの）。
     *
     * @param to 変更後の利用状態
     * @param reason 停止の理由
     */
    private record StatusRequest(UserStatus to, Optional<SuspensionReason> reason) {

        /** 知らない状態・理由の名前と、再開に理由を付けた送信は 400（役割の名前と同じ扱い）。 */
        static StatusRequest parse(String status, String reason) {
            if (status == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            UserStatus to;
            try {
                to = UserStatus.valueOf(status);
            } catch (IllegalArgumentException _) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            Optional<SuspensionReason> parsed = parseReason(reason);
            if (to == UserStatus.ACTIVE && parsed.isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            return new StatusRequest(to, parsed);
        }
    }

    /**
     * 詳細の画面に示す内容。
     *
     * @param user 利用者
     * @param selected 選んでいる役割
     * @param reason 選んでいる停止の理由
     * @param version フォームが持つ版
     * @param error 役割の変更の誤り（なければ null）
     * @param statusError 利用状態の変更の誤り（なければ null）
     */
    private record DetailForm(
            UserDetail user,
            Set<Role> selected,
            Optional<SuspensionReason> reason,
            long version,
            ErrorMessage error,
            ErrorMessage statusError) {}

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

        /** 利用状態の変更の拒否の文言（UI 設計 S-20 の表。Bolt 34d）。停止の理由の選び方の誤りだけ理由の欄へリンクする。 */
        static ErrorMessage of(StatusChangeRejection rejection, UserStatus current) {
            String text =
                    switch (rejection) {
                        case SELF_CHANGE -> SELF_STATUS_NOTE;
                        case OVERSIGHT_ROLE -> OVERSIGHT_STATUS_NOTE;
                        case NO_CHANGE -> "利用状態は既に" + statusName(current) + "です。最新の内容を確かめてください。";
                        case REASON_REQUIRED -> "停止の理由を選んでください。";
                    };
            return new ErrorMessage(text, rejection == StatusChangeRejection.REASON_REQUIRED);
        }
    }
}
