package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.queryservices.AssignableCompanyQuery;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

/**
 * S-20 の利用者の詳細の読み出しと描画と、役割の変更・利用状態の変更が共有する拒否の枝（Bolt 34f。詳細の画面の部品）。範囲外の試行の
 * 記録（`ADMIN_SCOPE_DENIED`）の書き手はこの部品の 1 か所で、確定の POST から呼ばれたときだけ書く（GET は CSRF の守りがなく他サイトから
 * 記録を書かせられるので書かない。Bolt 34b の開発レビューの決定）。合成ルートの {@code @Bean} の戻り値の型の規則（AT-07）に当たらない
 * よう、{@code @Component} で組み立てる。
 *
 * <p>詳細に操作を足すとき（Bolt 38 の開始準備の決定 3・5）: (1) {@link Section} に操作の値を 1 つ足す（エラー要約の見出しと、選び方の
 * 誤りの欄の表示名。選び方の誤りのない操作は表示名を持たない）、(2) {@link Fields} にその欄を描くかを 1 つ足し、{@link #render} で
 * 1 度だけ決めて model に置く（テンプレートの欄を描く条件もそれを読む）、(3) {@link #anchor} の switch に、描く群の最初の選択肢を
 * リンク先にする枝を足す（値を足すと網羅の誤りでコンパイルが止まる）、(4) テンプレートに領域を足す（1 つの領域に操作が 2 つなら値を
 * 2 つ対応させる）、(5) controller は {@link Operation} を渡す。選び方の欄のない操作（Bolt 38c のログインの資格）は (2) を飛ばし、(3) で
 * 空を返す（拒否は文言だけを示す）。Bolt 38e の担当範囲は、担当範囲のある役割の利用者だけに領域を出し、(2) の代わりに {@link AssignmentArea} を描く。
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

    /** 自分の詳細のログインの資格の領域の案内（UI 設計 S-20。Bolt 38c）。ログインの資格の拒否の文言と同じ。 */
    static final String SELF_CREDENTIAL_NOTE = "自分のログインの資格は、この画面では変えられません。";

    /** 担当企業がないときの担当範囲の領域の案内（UI 設計 S-20。Bolt 38e。括弧の文は業務の側が効く 38g で外す）。 */
    static final String NO_ASSIGNMENT_NOTE = "担当企業はまだありません。担当企業は、業務の画面で扱える企業を絞るために使います。";

    /** 担当企業にできる荷主が操作者の管理範囲にないときの担当範囲の領域の案内（UI 設計 S-20。Bolt 38e の開発レビューの低-1）。 */
    static final String NO_SELECTABLE_NOTE = "あなたの管理範囲には、担当企業にできる荷主の企業がありません。";

    /** 監督の役割を持つ利用者の詳細のログインの資格の領域の案内（UI 設計 S-20。Bolt 38c）。ログインの資格の拒否の文言と同じ。 */
    static final String OVERSIGHT_CREDENTIAL_NOTE = "システム管理者・監査担当者のログインの資格は、この画面では変えられません。運用の手順に従ってください。";

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

    /** 操作者が担当企業にできる企業（Bolt 38e。担当範囲の変更の確認が企業名を示すのに使う）。権限がなければ 403。 */
    List<Company> assignableCompanies(AuthenticatedActor actor) {
        return queryService.assignableCompanies(actor).itemsOr(UserDetailPage::forbidden);
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
        List<RoleOption> roleOptions = roleOptions(assignable, form.selected());
        List<ReasonOption> reasonOptions = reasonOptions(form.reason());
        String statusNote = statusNote(user, self);
        // 担当範囲の領域は担当範囲のある役割の利用者だけに出す（Bolt 38e。どの役割かは規則だけが知る）
        AssignmentArea assignments = RoleAssignmentPolicy.holdsAssignmentScopedRole(user.roles())
                ? AssignmentArea.of(user, form.companies(), assignableCompanies(actor))
                : null;
        // 欄を描くかはここで 1 度だけ決め、テンプレートとエラー要約のリンク先の両方が読む（Bolt 38）
        Fields fields =
                new Fields(!self && !assignable.isEmpty(), statusNote == null && user.status() == UserStatus.ACTIVE);
        model.addAttribute("self", self);
        model.addAttribute("selfRoleNote", SELF_ROLE_NOTE);
        model.addAttribute("assignable", !assignable.isEmpty());
        model.addAttribute("roleForm", fields.roles());
        model.addAttribute("reasonForm", fields.reason());
        model.addAttribute("roleOptions", roleOptions);
        // 画面で付け外しできない今の役割（監督の役割など）は、変えずに送るため隠し項目で持つ
        model.addAttribute(
                "keptRoles",
                UserAdministrationLabels.order(user.roles()).stream()
                        .filter(role -> !assignable.contains(role))
                        .map(Role::name)
                        .toList());
        model.addAttribute("keptRoleNames", UserAdministrationLabels.roles(unassignable(user.roles(), assignable)));
        model.addAttribute("statusNote", statusNote);
        model.addAttribute("suspension", SuspensionLines.of(user));
        model.addAttribute("reasonOptions", reasonOptions);
        model.addAttribute(UserAdministrationLabels.VERSION_ATTRIBUTE, form.version());
        model.addAttribute("error", form.errorOf(Section.ROLES));
        model.addAttribute("statusError", form.errorOf(Section.STATUS));
        model.addAttribute(
                "summary",
                ErrorSummary.of(
                        form,
                        section -> anchor(
                                section,
                                fields.roles() ? roleOptions : List.of(),
                                fields.reason() ? reasonOptions : List.of(),
                                assignments == null ? List.of() : assignments.options())));
        model.addAttribute("credentials", LoginCredentials.of(user, self));
        model.addAttribute("assignments", assignments);
        model.addAttribute("assignmentError", form.errorOf(Section.ASSIGNMENTS));
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
                                new Problem(operation.section(), retry.message()),
                                retry.companies()),
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

    /**
     * その操作の選び方の誤りの欄へのリンク先（描く群の最初の選択肢。fieldset はフォーカスを受けないので選択肢へ向ける。Bolt 38）。欄を
     * 描かないときは選択肢を空で渡すので、リンク先も空。
     */
    private static Optional<String> anchor(
            Section section,
            List<RoleOption> drawnRoles,
            List<ReasonOption> drawnReasons,
            List<CompanyOption> drawnCompanies) {
        return switch (section) {
            case ROLES ->
                drawnRoles.stream()
                        .findFirst()
                        .map(option -> "#role-" + option.role().name());
            case STATUS ->
                drawnReasons.stream()
                        .findFirst()
                        .map(option -> "#reason-" + option.reason().name());
            case ASSIGNMENTS -> drawnCompanies.stream().findFirst().map(option -> "#company-" + option.id());
            // ログインの資格の操作は詳細に選び方の欄を持たない（拒否は文言だけを示す。Bolt 38c）
            case UNLOCK, TOTP_RESET -> Optional.empty();
        };
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

    /**
     * 利用状態の領域の停止の記録の行（Bolt 38c2。US-16 AC5、#50）。停止中の利用者だけに示す。記録がなければ「記録がありません」の 1 行。
     *
     * @param reason 停止の理由の行（記録がなければ null）
     * @param actor 停止した人の行（記録がなければ null）
     * @param occurredAt 停止した日時の行（記録がなければ null）
     * @param missing 記録がないときの行（記録があれば null）
     */
    public record SuspensionLines(String reason, String actor, String occurredAt, String missing) {

        /**
         * 停止の記録の行。利用中の利用者には示さないので null。照会のサービスは停止中の利用者にだけ記録を載せるが、ここでも状態を見る。
         * 空の記録を、停止中なら「記録がありません」、利用中なら何も示さない、と読み分けるため。
         */
        static SuspensionLines of(UserDetail user) {
            if (user.status() != UserStatus.SUSPENDED) {
                return null;
            }
            return user.suspension()
                    .map(SuspensionLines::recorded)
                    .orElseGet(() -> new SuspensionLines(null, null, null, "停止の記録: 記録がありません"));
        }

        private static SuspensionLines recorded(SuspensionRecord suspension) {
            return new SuspensionLines(
                    "停止の理由: " + UserAdministrationLabels.suspensionReason(suspension.reason()),
                    "停止した人: " + UserAdministrationLabels.suspensionActor(suspension),
                    "停止した日時: " + UserAdministrationLabels.staffTime(suspension.occurredAt()),
                    null);
        }
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

    /** 権限なしの応答（403、A-04）。一覧系の照会の権限なし（{@code AdministrationListing.NotPermitted}）もここに寄せる（Bolt 38e。#50 (b)）。 */
    static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /**
     * 誤りを示す詳細の操作（Bolt 34d で 2 つの要約を 1 つにした。Bolt 38 から 1 つの値が 1 つの操作）。エラー要約の見出しと、選び方の
     * 誤りの欄の表示名（リンクの文「表示名: 文言」に使う。共通部品のエラー要約）を決める。リンク先は描く欄で決まるので {@link #anchor}
     * が決める。
     */
    enum Section {
        /** 役割の変更。 */
        ROLES("役割を変更できませんでした", "付ける役割"),
        /** 利用状態の変更。 */
        STATUS("利用状態を変更できませんでした", "停止の理由"),
        /** ロックの解除（Bolt 38c。詳細に出す拒否だけ。選び方の欄はない）。 */
        UNLOCK("ロックを解除できませんでした", null),
        /** 認証アプリの登録の解除（Bolt 38c。詳細に出す拒否だけ。確認の画面の入力の誤りは確認の画面の要約で示す）。 */
        TOTP_RESET("認証アプリの登録を解除できませんでした", null),
        /** 担当範囲の変更（Bolt 38e）。 */
        ASSIGNMENTS("担当範囲を変更できませんでした", "担当企業");

        private final String title;
        private final String fieldLabel;

        /**
         * @param title エラー要約の見出し
         * @param fieldLabel 選び方の誤りの欄の表示名（選び方の誤りのない操作は null）
         */
        Section(String title, String fieldLabel) {
            this.title = title;
            this.fieldLabel = fieldLabel;
        }

        /** エラー要約の見出し（Bolt 38c。確認の画面の要約も同じ見出しを使う）。 */
        String title() {
            return title;
        }

        /** 選び方の誤りの欄の表示名。選び方の誤りのない操作は空。 */
        Optional<String> fieldLabel() {
            return Optional.ofNullable(fieldLabel);
        }
    }

    /**
     * 詳細のログインの資格の領域（Bolt 38c）。自分と監督の役割の利用者には操作を出さず案内だけを示す。操作は当てはまるときだけ出す
     * （ロックの解除はロック中、認証アプリの登録の解除は登録済み。停止中・荷受人の企業の利用者にも出す）。
     *
     * @param lockState ロックの状態の文
     * @param lockUntil ロックが解ける日時の文（Bolt 38c2 で状態の文から分けた。ロックがなければ null）
     * @param totpState 認証アプリの登録の状態の文
     * @param note 操作を出さない理由の案内（操作を出すなら null）
     * @param unlockable ロックの解除の操作を出すか
     * @param resettable 認証アプリの登録の解除の操作を出すか
     */
    public record LoginCredentials(
            String lockState, String lockUntil, String totpState, String note, boolean unlockable, boolean resettable) {

        static LoginCredentials of(UserDetail user, boolean self) {
            String note = credentialNote(user, self);
            return new LoginCredentials(
                    "ロック: " + UserAdministrationLabels.lockState(user),
                    user.lockedUntil()
                            .map(until -> "解ける日時: " + UserAdministrationLabels.staffTime(until))
                            .orElse(null),
                    user.totpEnrolledAt().isPresent() ? "認証アプリ: 登録済み" : "認証アプリ: 未登録",
                    note,
                    note == null && user.lockedUntil().isPresent(),
                    note == null && user.totpEnrolledAt().isPresent());
        }

        private static String credentialNote(UserDetail user, boolean self) {
            if (self) {
                return SELF_CREDENTIAL_NOTE;
            }
            if (RoleAssignmentPolicy.holdsOversightRole(user.roles())) {
                return OVERSIGHT_CREDENTIAL_NOTE;
            }
            return null;
        }
    }

    /**
     * この画面で変えられない担当企業の数（選べる企業の外の担当企業。管理範囲の外の企業と、管理範囲の中でも無効になった荷主など）。詳細の
     * 領域と確認の領域の両方がここで数える（Bolt 38e の開発レビューの中-1・中-3）。
     */
    static int unchangeableCount(UserDetail user, List<Company> assignable) {
        return user.assignedCompanies().outsideCountOf(AssignableCompanyQuery.scopeOf(assignable));
    }

    /** 詳細の担当範囲の領域の、この画面で変えられない担当企業の数の案内（UI 設計 S-20。名前は出さない。決定 (11)）。なければ null。 */
    static String unchangeableNote(int count) {
        return count == 0 ? null : "この画面で変えられない企業 " + count + " 社も担当しています。";
    }

    /**
     * 停止の確認の領域に示すこの利用者の担当企業（Bolt 38g の確認ポイント 19。UI 設計 S-20）。担当範囲のある役割の利用者だけに示し、
     * そうでなければ null。名前は選べる企業（操作者の管理範囲の中の有効な荷主）のものだけを並べ、この画面で変えられない企業は 38e の語で
     * 数だけを示す（決定 (11)）。付け替えは担当範囲の領域で行う。
     */
    String assignedCompaniesLine(UserDetail user, AuthenticatedActor actor) {
        if (!RoleAssignmentPolicy.holdsAssignmentScopedRole(user.roles())) {
            return null;
        }
        List<Company> assignable = assignableCompanies(actor);
        Set<CompanyId> current = user.assignedCompanies().companies();
        String names = assignable.stream()
                .filter(company -> current.contains(company.id()))
                .map(Company::name)
                .collect(Collectors.joining("、"));
        int unchangeable = unchangeableCount(user, assignable);
        if (names.isEmpty() && unchangeable > 0) {
            return "担当企業: この画面で変えられない企業 " + unchangeable + " 社を担当しています。";
        }
        String line = "担当企業: " + (names.isEmpty() ? "なし" : names) + "。";
        String note = unchangeableNote(unchangeable);
        return note == null ? line : line + note;
    }

    /** 確認の領域の現在の状態に添える、この画面で変えられない担当企業の数（UI 設計 S-20）。なければ空。 */
    static String unchangeableSuffix(int count) {
        return count == 0 ? "" : "（この画面で変えられない企業 " + count + " 社）";
    }

    /**
     * 詳細の担当範囲の領域（Bolt 38e。US-16 AC1）。担当範囲のある役割の利用者だけに出す。選べる企業（操作者の管理範囲の中の有効な荷主）を
     * チェックボックスで並べ、選んだ企業（確認から戻ったときと拒否のとき）か今の担当企業に印を付ける。この画面で変えられない担当企業は名前を
     * 出さず数だけ示す（決定 (11)）。選べる企業がなければ、選択肢も確認の操作も出さず理由を一文で示す（開発レビューの低-1）。
     *
     * @param options 担当企業の選択肢（リポジトリの順）
     * @param noneNote 担当企業がないときの案内（あれば null）
     * @param outsideNote この画面で変えられない担当企業の数の案内（なければ null）
     * @param unselectableNote 選べる企業がないときの案内（選べる企業があれば null。null のときだけ確認の操作を出す）
     */
    public record AssignmentArea(
            List<CompanyOption> options, String noneNote, String outsideNote, String unselectableNote) {

        public AssignmentArea {
            options = List.copyOf(options);
        }

        static AssignmentArea of(UserDetail user, Optional<Set<CompanyId>> selected, List<Company> assignable) {
            Set<CompanyId> current = user.assignedCompanies().companies();
            Set<CompanyId> checked = selected.orElse(current);
            return new AssignmentArea(
                    assignable.stream()
                            .map(company -> new CompanyOption(
                                    company.id().value().toString(), company.name(), checked.contains(company.id())))
                            .toList(),
                    current.isEmpty() ? NO_ASSIGNMENT_NOTE : null,
                    unchangeableNote(unchangeableCount(user, assignable)),
                    assignable.isEmpty() ? NO_SELECTABLE_NOTE : null);
        }
    }

    /**
     * 担当企業の選択肢。
     *
     * @param id 企業 ID
     * @param name 企業名
     * @param selected 選んでいるか
     */
    public record CompanyOption(String id, String name, boolean selected) {}

    /**
     * 詳細に描く選び方の欄。
     *
     * @param roles 役割の欄（自分と荷受人の企業の利用者には描かない）
     * @param reason 停止の理由の欄（自分・監督の役割・停止中の利用者には描かない）
     */
    private record Fields(boolean roles, boolean reason) {}

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
     * @param companies 選んでいる担当企業（Bolt 38e。担当範囲の変更でなければ空）
     */
    record Retry(
            Set<Role> selected,
            Optional<SuspensionReason> reason,
            long version,
            ErrorMessage message,
            Optional<Set<CompanyId>> companies) {

        /** 担当企業を選ばない操作の内容。 */
        Retry(Set<Role> selected, Optional<SuspensionReason> reason, long version, ErrorMessage message) {
            this(selected, reason, version, message, Optional.empty());
        }
    }

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
     * @param companies 選んでいる担当企業（Bolt 38e。なければ今の担当企業に印を付ける）
     */
    record DetailForm(
            UserDetail user,
            Set<Role> selected,
            Optional<SuspensionReason> reason,
            long version,
            Problem problem,
            Optional<Set<CompanyId>> companies) {

        /** 担当企業を選んでいない内容。 */
        DetailForm(
                UserDetail user, Set<Role> selected, Optional<SuspensionReason> reason, long version, Problem problem) {
            this(user, selected, reason, version, problem, Optional.empty());
        }

        /** 誤りのない内容。 */
        static DetailForm of(UserDetail user, Set<Role> selected, Optional<SuspensionReason> reason, long version) {
            return new DetailForm(user, selected, reason, version, null);
        }

        /** 誤りのない内容（確認から戻ったときの担当企業を選んだまま。Bolt 38e）。 */
        static DetailForm of(
                UserDetail user,
                Set<Role> selected,
                Optional<SuspensionReason> reason,
                long version,
                Optional<Set<CompanyId>> companies) {
            return new DetailForm(user, selected, reason, version, null, companies);
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
     * @param anchor 選び方の誤りの欄のリンク先（リンクしないなら null）
     * @param text 示す文（リンクするなら「表示名: 文言」、しないなら文言だけ）
     */
    public record ErrorSummary(String title, String anchor, String text) {

        /** 詳細に示す誤りの要約。誤りがなければ null。選び方の誤りで、欄を描くときだけ欄へリンクする（Bolt 38）。 */
        static ErrorSummary of(DetailForm form, Function<Section, Optional<String>> anchorOf) {
            Problem problem = form.problem();
            if (problem == null) {
                return null;
            }
            Section section = problem.section();
            ErrorMessage message = problem.message();
            return section.fieldLabel()
                    .filter(_ -> message.field())
                    .flatMap(label -> anchorOf.apply(section)
                            .map(href -> new ErrorSummary(section.title, href, label + ": " + message.text())))
                    .orElseGet(() -> new ErrorSummary(section.title, null, message.text()));
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
