package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 利用者。事前登録され、固定役割を持つ人（BR-14、BR-15）。
 * password の照合は Spring Security に任せ、この集約は業務上の規則（利用停止・所属企業の有効性。IA-INV-09）だけを持つ。
 * 利用者の登録は利用者の管理（US-16）で作る。Bolt 34 で役割の変更の判定と版（`app_user.version`）を、Bolt 34d で利用状態の変更（利用停止と
 * 再開）の判定を足した。
 */
@AggregateRoot
public final class User {

    /** 判定の結果と変更・生成が受ける承認の値の名前（null の確かめの文に使う）。 */
    private static final String APPROVAL = "approval";

    /** 判定の結果の拒否の名前（null の確かめの文に使う）。 */
    private static final String REJECTION = "rejection";

    private final UserId id;
    private final CompanyId companyId;
    private final EmailAddress email;
    private final String displayName;
    private final String passwordHash;
    private final UserStatus status;
    private final Set<Role> roles;
    private final long version;

    @SuppressWarnings("java:S107") // 利用者の項目（Bolt 34 で版を足した）をそろえて受ける
    private User(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        // 役割 0 件の守りはここの 1 か所（リポジトリの復元のデータの不整合もここで捕まえる。Bolt 38b、#50）
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("利用者 " + id.value() + " には役割が 1 つ以上要る（BR-15）");
        }
        this.roles = EnumSet.copyOf(roles);
        this.version = version;
    }

    /** 利用者を組み立てる（リポジトリの復元とテストが使う。登録は {@link #register}）。password はハッシュ（DelegatingPasswordEncoder の形式。SEC-07）で受け取る。 */
    public static User of(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles) {
        return of(id, companyId, email, displayName, passwordHash, status, roles, 0L);
    }

    /** 版（`app_user.version`）を持つ利用者を組み立てる（リポジトリの復元が使う。Bolt 34）。 */
    @SuppressWarnings("java:S107") // 保存されている項目をそろえて受ける
    public static User of(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles,
            long version) {
        return new User(id, companyId, email, displayName, passwordHash, status, roles, version);
    }

    /**
     * 認証できない理由を返す（IA-INV-09）。認証できるなら空。password の照合の後に確かめる（照合の前に拒否すると、誤った password でも利用停止が応答の時間から分かるため）。
     *
     * @param company 所属企業
     */
    public Optional<AuthenticationRejection> authenticationRejection(Company company) {
        if (!company.id().equals(companyId)) {
            throw new IllegalArgumentException("所属企業で確かめる");
        }
        if (status == UserStatus.SUSPENDED) {
            return Optional.of(AuthenticationRejection.SUSPENDED);
        }
        if (!company.active()) {
            return Optional.of(AuthenticationRejection.COMPANY_INACTIVE);
        }
        return Optional.empty();
    }

    /**
     * password を変えた利用者を返す（Bolt 33a、US-18 AC8）。新しい password はハッシュ（DelegatingPasswordEncoder の形式。SEC-07）で
     * 受け取る。方針（SEC-04・SEC-05）は呼ぶ側が先に確かめる。
     */
    public User changePassword(String newPasswordHash) {
        return new User(id, companyId, email, displayName, newPasswordHash, status, roles, version);
    }

    /**
     * 役割の変更を判定する（Bolt 34。US-16 AC3、UC-16 の主シナリオ 4。Bolt 34e で承認の値を返す形にした）。受け付けるなら承認の値
     * （{@link RoleChangeApproval}。判定したこの利用者の実体と新しい役割を持つ）を、受け付けないなら理由を返す。判定は、自分の変更 →
     * 役割なし → 変更なし → 監督の役割 → 企業の種類 → 職務分離の順。同時の更新は版を条件にした更新で判定するので、ここでは見ない。
     *
     * @param operator 変更する操作者
     * @param company 所属企業
     * @param newRoles 新しい役割
     */
    public RoleChangeDecision decideRoleChange(UserId operator, Company company, Set<Role> newRoles) {
        return roleChangeRejection(operator, company, newRoles)
                .<RoleChangeDecision>map(RoleChangeDecision.Refused::new)
                .orElseGet(() -> new RoleChangeDecision.Approved(new RoleChangeApproval(this, newRoles)));
    }

    private Optional<RoleChangeRejection> roleChangeRejection(UserId operator, Company company, Set<Role> newRoles) {
        if (!company.id().equals(companyId)) {
            throw new IllegalArgumentException("所属企業で確かめる");
        }
        if (id.equals(operator)) {
            return Optional.of(RoleChangeRejection.of(RoleChangeRejectionReason.SELF_CHANGE));
        }
        if (newRoles.isEmpty()) {
            return Optional.of(RoleChangeRejection.of(RoleChangeRejectionReason.NO_ROLES));
        }
        if (roles.equals(EnumSet.copyOf(newRoles))) {
            return Optional.of(RoleChangeRejection.of(RoleChangeRejectionReason.NO_CHANGE));
        }
        Set<Role> oversight = RoleAssignmentPolicy.oversightChanges(roles, newRoles);
        if (!oversight.isEmpty()) {
            return Optional.of(new RoleChangeRejection(RoleChangeRejectionReason.OVERSIGHT_ROLE, oversight));
        }
        Set<Role> unfit = RoleAssignmentPolicy.unfitRoles(company.kind(), newRoles);
        if (!unfit.isEmpty()) {
            return Optional.of(new RoleChangeRejection(RoleChangeRejectionReason.UNFIT_FOR_COMPANY, unfit));
        }
        Set<Role> conflicts = RoleAssignmentPolicy.conflicts(newRoles);
        if (!conflicts.isEmpty()) {
            return Optional.of(new RoleChangeRejection(RoleChangeRejectionReason.CONFLICTING_ROLES, conflicts));
        }
        return Optional.empty();
    }

    /**
     * 役割を変えた利用者を返す（Bolt 34）。判定（{@link #decideRoleChange}）が通ったときの承認の値だけを受ける（Bolt 34e。判定を通さずに
     * 呼べない）。承認がこの利用者そのもの（同じ実体）の判定で得たものでなければ例外（取り違えの守り。2026-10-17、human:kakimomokuri）。
     * 集約は不変なので、別の利用者・判定に使った版と違う利用者・同じ ID と版で組み立てた別の利用者・変えた後の利用者に同じ承認を
     * もう一度渡すことを捕まえる。同時の更新の判定はリポジトリの更新の件数で行う。版は変えない（リポジトリが上げる）。
     */
    public User changeRoles(RoleChangeApproval approval) {
        requireDecidedOnThis(approval.decidedOn);
        return new User(id, companyId, email, displayName, passwordHash, status, approval.roles(), version);
    }

    /**
     * 利用状態の変更を判定する（Bolt 34d。US-16 AC5、IA-INV-01。Bolt 34e で承認の値を返す形にした）。受け付けるなら承認の値
     * （{@link StatusChangeApproval}）を、受け付けないなら理由を返す。再開の承認は停止の理由を持たない（渡された理由を捨てる。
     * 2026-10-17、human:kakimomokuri）。
     *
     * @param operator 変更する操作者
     * @param to 変更後の利用状態
     * @param reason 停止の理由（停止のときに要る）
     */
    public StatusChangeDecision decideStatusChange(UserId operator, UserStatus to, Optional<SuspensionReason> reason) {
        return statusChangeRejection(operator, to, reason)
                .<StatusChangeDecision>map(StatusChangeDecision.Refused::new)
                .orElseGet(() -> new StatusChangeDecision.Approved(
                        new StatusChangeApproval(this, to, to == UserStatus.SUSPENDED ? reason : Optional.empty())));
    }

    /**
     * 利用状態の変更を受け付けない理由。受け付けるなら空。判定は、自分の変更 → 監督の役割 →
     * 変更なし → 停止の理由なしの順。監督の役割を今の状態より先に見るのは、監督の役割を持つ利用者を状態にかかわらず画面で扱わないため
     * （拒否の種類でその利用者の状態を区別しない）。再開は理由を見ない。同時の更新は版を条件にした更新で判定するので、ここでは見ない。
     *
     * @param operator 変更する操作者
     * @param to 変更後の利用状態
     * @param reason 停止の理由（停止のときに要る）
     */
    private Optional<StatusChangeRejection> statusChangeRejection(
            UserId operator, UserStatus to, Optional<SuspensionReason> reason) {
        if (id.equals(operator)) {
            return Optional.of(StatusChangeRejection.SELF_CHANGE);
        }
        if (RoleAssignmentPolicy.holdsOversightRole(roles)) {
            return Optional.of(StatusChangeRejection.OVERSIGHT_ROLE);
        }
        if (status == to) {
            return Optional.of(StatusChangeRejection.NO_CHANGE);
        }
        if (to == UserStatus.SUSPENDED && reason.isEmpty()) {
            return Optional.of(StatusChangeRejection.REASON_REQUIRED);
        }
        return Optional.empty();
    }

    /**
     * 利用状態を変えた利用者を返す（Bolt 34d）。判定（{@link #decideStatusChange}）が通ったときの承認の値だけを受ける（Bolt 34e）。
     * 承認がこの利用者そのもの（同じ実体）の判定で得たものでなければ例外（取り違えの守り。{@link #changeRoles} と同じく、変えた後の
     * 利用者に同じ承認をもう一度渡すことも捕まえる）。版は変えない（リポジトリが上げる）。
     */
    public User changeStatus(StatusChangeApproval approval) {
        requireDecidedOnThis(approval.decidedOn);
        return new User(id, companyId, email, displayName, passwordHash, approval.to(), roles, version);
    }

    /** 承認がこの実体の判定で得たものかを確かめる（値で比べない。同じ ID と版の別の実体の承認も拒む）。 */
    private void requireDecidedOnThis(User decidedOn) {
        if (decidedOn != this) {
            throw new IllegalArgumentException("この利用者の判定で得た承認ではない");
        }
    }

    public UserId id() {
        return id;
    }

    public CompanyId companyId() {
        return companyId;
    }

    public EmailAddress email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    /** 版（`app_user.version`。同時の更新の検出に使う。Bolt 34）。 */
    public long version() {
        return version;
    }

    /**
     * 利用者の登録を判定する（Bolt 38b。US-16 AC1・AC3、UC-16 の主シナリオ 1）。受け付けるなら承認の値（{@link UserRegistrationApproval}）を、
     * 受け付けないなら理由を返す。判定は、役割なし → 監督の役割 → 企業の種類 → 職務分離の順（役割の変更と同じ順）。管理範囲・企業の
     * 有効性・重複のメールアドレスは登録のサービスが見る。
     *
     * @param company 所属企業
     * @param roles 付ける役割
     */
    public static UserRegistrationDecision decideRegistration(Company company, Set<Role> roles) {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(roles, "roles");
        return registrationRejection(company, roles)
                .<UserRegistrationDecision>map(UserRegistrationDecision.Refused::new)
                .orElseGet(
                        () -> new UserRegistrationDecision.Approved(new UserRegistrationApproval(company.id(), roles)));
    }

    private static Optional<UserRegistrationRejection> registrationRejection(Company company, Set<Role> roles) {
        if (roles.isEmpty()) {
            return Optional.of(new UserRegistrationRejection(UserRegistrationRejectionReason.NO_ROLES, Set.of()));
        }
        Set<Role> oversight = RoleAssignmentPolicy.oversightChanges(Set.of(), roles);
        if (!oversight.isEmpty()) {
            return Optional.of(
                    new UserRegistrationRejection(UserRegistrationRejectionReason.OVERSIGHT_ROLE, oversight));
        }
        Set<Role> unfit = RoleAssignmentPolicy.unfitRoles(company.kind(), roles);
        if (!unfit.isEmpty()) {
            return Optional.of(new UserRegistrationRejection(UserRegistrationRejectionReason.UNFIT_FOR_COMPANY, unfit));
        }
        Set<Role> conflicts = RoleAssignmentPolicy.conflicts(roles);
        if (!conflicts.isEmpty()) {
            return Optional.of(
                    new UserRegistrationRejection(UserRegistrationRejectionReason.CONFLICTING_ROLES, conflicts));
        }
        return Optional.empty();
    }

    /**
     * 登録した利用者を作る（Bolt 38b）。判定（{@link #decideRegistration}）が通ったときの承認の値だけを受け、版 0・利用中の利用者を返す。
     * 承認は 1 回の登録に使う（判定は static なので判定した実体に縛られない。重複は登録のサービスとメールアドレスの一意制約で守る）。
     *
     * @param approval 登録の承認
     * @param id 利用者 ID
     * @param email メールアドレス
     * @param displayName 表示名
     * @param passwordHash password のハッシュ（初回は使えないハッシュ）
     */
    public static User register(
            UserRegistrationApproval approval, UserId id, EmailAddress email, String displayName, String passwordHash) {
        Objects.requireNonNull(approval, APPROVAL);
        return new User(
                id, approval.companyId(), email, displayName, passwordHash, UserStatus.ACTIVE, approval.roles, 0L);
    }

    /** 利用者の登録の判定の結果（Bolt 38b）。 */
    public sealed interface UserRegistrationDecision {

        /**
         * 受け付ける。
         *
         * @param approval 承認の値
         */
        record Approved(UserRegistrationApproval approval) implements UserRegistrationDecision {

            public Approved {
                Objects.requireNonNull(approval, APPROVAL);
            }
        }

        /**
         * 受け付けない。
         *
         * @param rejection 理由と示す役割
         */
        record Refused(UserRegistrationRejection rejection) implements UserRegistrationDecision {

            public Refused {
                Objects.requireNonNull(rejection, REJECTION);
            }
        }
    }

    /**
     * 利用者の登録の承認。判定（{@link #decideRegistration}）を通った印で、業務の承認の手続きではない（Bolt 38b）。コンストラクタを private に
     * し、利用者の集約だけが作る（役割の変更の承認と同じ形）。所属企業（ID。別の集約は ID で参照する）と役割を持つ。判定を通った印で、値として比べない（equals を持たない）。
     */
    @ValueObject
    public static final class UserRegistrationApproval {

        private final CompanyId companyId;
        private final Set<Role> roles;

        private UserRegistrationApproval(CompanyId companyId, Set<Role> roles) {
            this.companyId = Objects.requireNonNull(companyId, "companyId");
            this.roles = Set.copyOf(roles);
        }

        public CompanyId companyId() {
            return companyId;
        }

        public Set<Role> roles() {
            return Set.copyOf(roles);
        }
    }

    /** 役割の変更の判定の結果（Bolt 34e）。 */
    public sealed interface RoleChangeDecision {

        /**
         * 受け付ける。
         *
         * @param approval 承認の値
         */
        record Approved(RoleChangeApproval approval) implements RoleChangeDecision {

            public Approved {
                Objects.requireNonNull(approval, APPROVAL);
            }
        }

        /**
         * 受け付けない。
         *
         * @param rejection 理由と示す役割
         */
        record Refused(RoleChangeRejection rejection) implements RoleChangeDecision {

            public Refused {
                Objects.requireNonNull(rejection, REJECTION);
            }
        }
    }

    /** 利用状態の変更の判定の結果（Bolt 34e）。 */
    public sealed interface StatusChangeDecision {

        /**
         * 受け付ける。
         *
         * @param approval 承認の値
         */
        record Approved(StatusChangeApproval approval) implements StatusChangeDecision {

            public Approved {
                Objects.requireNonNull(approval, APPROVAL);
            }
        }

        /**
         * 受け付けない。
         *
         * @param rejection 理由
         */
        record Refused(StatusChangeRejection rejection) implements StatusChangeDecision {

            public Refused {
                Objects.requireNonNull(rejection, REJECTION);
            }
        }
    }

    /**
     * 役割の変更の承認。判定（{@link #decideRoleChange}）を通った印で、業務の承認の手続き（荷主承認・社内承認）ではない（Bolt 34e）。
     * コンストラクタを private にし、利用者の集約だけが作る（同じパッケージからも作れない。作り手を集約に限るため、valueobjects でなく
     * 集約の中に置く）。持つのは判定した利用者の実体と新しい役割で、対象の利用者 ID と判定に使った版はその実体から取る（承認を判定した
     * 実体で縛る。2026-10-17、human:kakimomokuri）。判定を通った印で、値として比べない（equals を持たない）。
     */
    @ValueObject
    public static final class RoleChangeApproval {

        private final User decidedOn;
        private final Set<Role> roles;

        private RoleChangeApproval(User decidedOn, Set<Role> roles) {
            this.decidedOn = Objects.requireNonNull(decidedOn, "decidedOn");
            this.roles = EnumSet.copyOf(roles);
        }

        public UserId userId() {
            return decidedOn.id;
        }

        public long version() {
            return decidedOn.version;
        }

        public Set<Role> roles() {
            return Set.copyOf(roles);
        }
    }

    /**
     * 利用状態の変更の承認。判定（{@link #decideStatusChange}）を通った印で、業務の承認の手続きではない（Bolt 34e）。コンストラクタを
     * private にし、利用者の集約だけが作る。持つのは判定した利用者の実体・変更後の利用状態・停止の理由（再開の承認は理由を持たない）で、
     * 対象の利用者 ID と判定に使った版はその実体から取る（2026-10-17、human:kakimomokuri）。判定を通った印で、値として比べない
     * （equals を持たない）。
     */
    @ValueObject
    public static final class StatusChangeApproval {

        private final User decidedOn;
        private final UserStatus to;
        private final SuspensionReason reason;

        private StatusChangeApproval(User decidedOn, UserStatus to, Optional<SuspensionReason> reason) {
            this.decidedOn = Objects.requireNonNull(decidedOn, "decidedOn");
            this.to = Objects.requireNonNull(to, "to");
            this.reason = reason.orElse(null);
        }

        public UserId userId() {
            return decidedOn.id;
        }

        public long version() {
            return decidedOn.version;
        }

        public UserStatus to() {
            return to;
        }

        /** 停止の理由。再開の承認なら空。 */
        public Optional<SuspensionReason> reason() {
            return Optional.ofNullable(reason);
        }
    }
}
