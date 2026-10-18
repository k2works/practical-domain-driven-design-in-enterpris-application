package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

/**
 * S-20 の利用者の詳細の読み出しと描画と、役割の変更・利用状態の変更が共有する拒否の枝（Bolt 34f。詳細の画面の部品）。範囲外の試行の
 * 記録（`ADMIN_SCOPE_DENIED`）の書き手はこの部品の 1 か所で、確定の POST から呼ばれたときだけ書く（GET は CSRF の守りがなく他サイトから
 * 記録を書かせられるので書かない。Bolt 34b の開発レビューの決定）。合成ルートの {@code @Bean} の戻り値の型の規則（AT-07）に当たらない
 * よう、{@code @Component} で組み立てる。
 */
@Component
final class UserDetailPage {

    /** 先に更新されていたときの文言（UI 設計 S-20。誰が変えたかは言わない）。 */
    static final String STALE_MESSAGE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

    /** 自分の詳細の役割の領域の案内（UI 設計 S-20）。役割の変更の自分の役割の拒否の文言と同じ。 */
    static final String SELF_ROLE_NOTE = "自分の役割は変更できません。ほかのシステム管理者に依頼してください。";

    /** 自分の詳細の利用状態の領域の案内（UI 設計 S-20。Bolt 34d）。利用状態の変更の拒否の文言と同じ。 */
    static final String SELF_STATUS_NOTE = "自分の利用状態は変更できません。ほかのシステム管理者に依頼してください。";

    /** 監督の役割を持つ利用者の詳細の利用状態の領域の案内（UI 設計 S-20。Bolt 34d）。利用状態の変更の拒否の文言と同じ。 */
    static final String OVERSIGHT_STATUS_NOTE = "システム管理者・監査担当者の利用停止と再開は、この画面では行いません。";

    private static final String DETAIL_VIEW = "identity/users/detail";

    private final UserAdministrationQueryService queryService;
    private final AdministrationScopeDenialService denials;

    UserDetailPage(UserAdministrationQueryService queryService, AdministrationScopeDenialService denials) {
        this.queryService = queryService;
        this.denials = denials;
    }

    /**
     * 詳細を読む。いなければ 404、管理範囲の外なら同じ 404（存在を示さない。Bolt 34b の決定 3）、システム管理者でなければ 403（変更の
     * 権限なしと同じ応答）。範囲外の試行は、確定（{@code recordDenial}）のときだけ記録する。
     */
    UserDetail find(String id, AuthenticatedActor actor, boolean recordDenial) {
        return find(UserAdministrationParameters.userId(id), actor, recordDenial);
    }

    private UserDetail find(UserId target, AuthenticatedActor actor, boolean recordDenial) {
        return switch (queryService.detail(actor, target)) {
            case UserLookup.Found(UserDetail found) -> found;
            case UserLookup.NotFound _ -> throw notFound();
            case UserLookup.OutOfScope _ -> {
                if (recordDenial) {
                    denials.recordDenied(actor, target);
                }
                throw notFound();
            }
            case UserLookup.NotPermitted _ -> throw forbidden();
        };
    }

    /** 詳細の画面を描く。 */
    String render(DetailForm form, AuthenticatedActor actor, Model model) {
        UserDetail user = form.user();
        boolean self = user.id().equals(actor.userId());
        Set<Role> assignable = RoleAssignmentPolicy.assignableRoles(user.companyKind());
        model.addAttribute("user", user);
        model.addAttribute("statusName", UserAdministrationLabels.status(user.status()));
        model.addAttribute("currentRoles", UserAdministrationLabels.roles(user.roles()));
        model.addAttribute("companyKindName", UserAdministrationLabels.companyKind(user.companyKind()));
        model.addAttribute("self", self);
        model.addAttribute("selfRoleNote", SELF_ROLE_NOTE);
        model.addAttribute("assignable", !assignable.isEmpty());
        model.addAttribute("roleOptions", roleOptions(assignable, form.selected()));
        // 画面で付け外しできない今の役割（監督の役割など）は、変えずに送るため隠し項目で持つ
        model.addAttribute(
                "keptRoles",
                UserAdministrationLabels.order(user.roles()).stream()
                        .filter(role -> !assignable.contains(role))
                        .map(Role::name)
                        .toList());
        model.addAttribute("keptRoleNames", UserAdministrationLabels.roles(unassignable(user.roles(), assignable)));
        model.addAttribute("statusNote", statusNote(user, self));
        model.addAttribute("reasonOptions", reasonOptions(form.reason()));
        model.addAttribute(UserAdministrationLabels.VERSION_ATTRIBUTE, form.version());
        model.addAttribute("error", form.errorOf(Section.ROLES));
        model.addAttribute("statusError", form.errorOf(Section.STATUS));
        model.addAttribute("summary", ErrorSummary.of(form));
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
        return DETAIL_VIEW;
    }

    /**
     * 受け付けなかった結果の画面。規則の拒否は {@code operation} が作る内容（選んだまま）で、古い版は最新の内容で詳細を示し直す。
     * どちらも誤りは {@code operation} の領域に示す。詳細を読んだ後に管理範囲から外れたとき（同じ request の中の守り）の範囲外の
     * 試行は、確定（{@code recordDenial}）のときだけ記録する。
     */
    <R> String refuse(
            UserId target,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<R> refusal,
            boolean recordDenial,
            Operation<R> operation) {
        return switch (refusal) {
            case UserAdministrationRefusal.Rejected<R>(R rejection) -> {
                UserDetail user = find(target, actor, false);
                Retry retry = operation.onRejected().apply(user, rejection);
                yield render(
                        new DetailForm(
                                user,
                                retry.selected(),
                                retry.reason(),
                                retry.version(),
                                new Problem(operation.section(), retry.message())),
                        actor,
                        model);
            }
            case UserAdministrationRefusal.Stale<R> _ -> {
                UserDetail user = find(target, actor, false);
                yield render(
                        new DetailForm(
                                user,
                                user.roles(),
                                Optional.empty(),
                                user.version(),
                                new Problem(operation.section(), new ErrorMessage(STALE_MESSAGE, false))),
                        actor,
                        model);
            }
            case UserAdministrationRefusal.NotFound<R> _ -> throw notFound();
            case UserAdministrationRefusal.OutOfScope<R> _ -> {
                if (recordDenial) {
                    denials.recordDenied(actor, target);
                }
                throw notFound();
            }
            case UserAdministrationRefusal.NotPermitted<R> _ -> throw forbidden();
        };
    }

    private static Set<Role> unassignable(Set<Role> roles, Set<Role> assignable) {
        Set<Role> result = EnumSet.noneOf(Role.class);
        roles.stream().filter(role -> !assignable.contains(role)).forEach(result::add);
        return result;
    }

    /** 画面で付け外しできる役割（企業の種類に合う業務の役割）の選択肢。宣言の順。 */
    private static List<RoleOption> roleOptions(Set<Role> assignable, Set<Role> selected) {
        return Arrays.stream(Role.values())
                .filter(assignable::contains)
                .map(role -> new RoleOption(role, selected.contains(role)))
                .toList();
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
                .map(reason -> new ReasonOption(
                        reason,
                        UserAdministrationLabels.suspensionReason(reason),
                        selected.equals(Optional.of(reason))))
                .toList();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /** 誤りを示す詳細の領域。エラー要約の見出しとリンク先を決める（Bolt 34d で 2 つの要約を 1 つにした）。 */
    enum Section {
        /** 役割の領域。 */
        ROLES("役割を変更できませんでした", "#roles"),
        /** 利用状態の領域。 */
        STATUS("利用状態を変更できませんでした", "#suspension-reason");

        private final String title;
        private final String anchor;

        Section(String title, String anchor) {
            this.title = title;
            this.anchor = anchor;
        }
    }

    /**
     * 拒否の画面を描く操作（役割の変更・利用状態の変更）。誤りを示す領域は操作ごとに 1 つで、規則の拒否と古い版の両方に使う。
     *
     * @param section 誤りを示す領域
     * @param onRejected 規則の拒否のときに、読み直した利用者と拒否から示し直す内容を作る
     */
    record Operation<R>(Section section, BiFunction<UserDetail, R, Retry> onRejected) {}

    /**
     * 規則の拒否のときに示し直す内容（選んだまま、送られた版のまま）。
     *
     * @param selected 選んでいる役割
     * @param reason 選んでいる停止の理由
     * @param version フォームが持つ版
     * @param message 誤りの文言
     */
    record Retry(Set<Role> selected, Optional<SuspensionReason> reason, long version, ErrorMessage message) {}

    /**
     * 詳細に示す誤り。
     *
     * @param section 誤りを示す領域
     * @param message 誤りの文言
     */
    record Problem(Section section, ErrorMessage message) {}

    /**
     * 詳細の画面に示す内容。
     *
     * @param user 利用者
     * @param selected 選んでいる役割
     * @param reason 選んでいる停止の理由
     * @param version フォームが持つ版
     * @param problem 誤り（なければ null）
     */
    record DetailForm(
            UserDetail user, Set<Role> selected, Optional<SuspensionReason> reason, long version, Problem problem) {

        /** 誤りのない内容。 */
        static DetailForm of(UserDetail user, Set<Role> selected, Optional<SuspensionReason> reason, long version) {
            return new DetailForm(user, selected, reason, version, null);
        }

        /** その領域の誤り。ほかの領域の誤りか、誤りがなければ null。 */
        ErrorMessage errorOf(Section of) {
            return problem != null && problem.section() == of ? problem.message() : null;
        }
    }

    /**
     * 役割の選択肢。
     *
     * @param role 役割
     * @param selected 選んでいるか
     */
    public record RoleOption(Role role, boolean selected) {}

    /**
     * 停止の理由の選択肢。
     *
     * @param reason 理由
     * @param name 画面の名前
     * @param selected 選んでいるか
     */
    public record ReasonOption(SuspensionReason reason, String name, boolean selected) {}

    /**
     * 詳細のエラー要約。
     *
     * @param title 見出し
     * @param error 誤り
     * @param anchor 選び方の誤りのときにリンクする欄
     */
    public record ErrorSummary(String title, ErrorMessage error, String anchor) {

        /** 詳細に示す誤りの要約。誤りがなければ null。 */
        static ErrorSummary of(DetailForm form) {
            Problem problem = form.problem();
            if (problem == null) {
                return null;
            }
            return new ErrorSummary(problem.section().title, problem.message(), problem.section().anchor);
        }
    }

    /**
     * 誤りの文言。
     *
     * @param text 文言
     * @param field 選び方の誤りか（エラー要約から欄へリンクし、欄にも示す。古い版と、役割の変更の自分の役割、利用状態の変更の自分と
     *     監督の役割の拒否は、選び方の誤りではない）
     */
    public record ErrorMessage(String text, boolean field) {}
}
