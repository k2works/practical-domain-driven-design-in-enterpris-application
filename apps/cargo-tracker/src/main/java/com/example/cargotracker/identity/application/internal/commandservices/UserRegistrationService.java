package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.DuplicateEmailException;
import com.example.cargotracker.identity.domain.model.aggregates.UnusablePasswordHashIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の登録のサービス。システム管理者が管理範囲の企業に利用者を登録する（Bolt 38b2。US-16 AC1・AC2・AC3、UC-16 の主シナリオ 1 と拡張 2a）。
 *
 * <p>判定は、操作者 → 企業の有無 → 管理範囲 → 企業の有効性（範囲の外の企業に理由を示さないため範囲の後）→ 表示名 → 登録の判定
 * （{@link User#decideRegistration}。役割なし・監督の役割・企業の種類・職務分離）→ 重複のメールアドレスの順。受け付けたら、使えないハッシュの
 * 利用者と、付与した操作者付きの役割と、監査記録 {@code USER_REGISTERED}（役割だけ。メールアドレスと表示名は書かない）を 1 つの
 * トランザクションで書く。監査を書けなければ例外で全体を戻す（IA-INV-08 の同期の書き方）。同じメールアドレスの同時の登録は一意制約の
 * 例外を値にする（トランザクションの外で捕まえる。前例 {@code TotpEnrollmentService}）。拒否は監査に書かない。確定で管理範囲の外の企業へ
 * 登録しようとしたときだけ、その試行を別のトランザクションで記録する（IA-INV-14。登録の範囲外の記録の書き手はこの 1 か所。Bolt 38b2 の
 * 開始準備の決定 (5)）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserRegistrationService {

    /** 表示名の長さの上限（コードポイント。`app_user.display_name` は `VARCHAR(200)`）。 */
    private static final int DISPLAY_NAME_MAX = 200;

    private final UserRepository users;
    private final CompanyRepository companies;
    private final UserAssignmentRepository assignments;
    private final AuditRecordRepository auditRecords;
    private final UnusablePasswordHashIssuer unusablePasswordHashes;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("java:S107") // 判定と書き込みと使えないハッシュの部品をそろえて受ける
    public UserRegistrationService(
            UserRepository users,
            CompanyRepository companies,
            UserAssignmentRepository assignments,
            AuditRecordRepository auditRecords,
            UnusablePasswordHashIssuer unusablePasswordHashes,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.companies = companies;
        this.assignments = assignments;
        this.auditRecords = auditRecords;
        this.unusablePasswordHashes = unusablePasswordHashes;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 登録を受け付けられるかを確かめる（確認の画面を開く時。何も書かない。範囲外の試行も記録しない）。
     *
     * @param operator 操作者
     * @param companyId 所属させる企業
     * @param email メールアドレス（形は画面が確かめる）
     * @param displayName 表示名
     * @param roles 付ける役割
     */
    public UserRegistrationCheck check(
            AuthenticatedActor operator, CompanyId companyId, EmailAddress email, String displayName, Set<Role> roles) {
        return switch (judge(operator, companyId, email, displayName, roles)) {
            case Judgement.Refused(var refusal) -> new UserRegistrationCheck.Refused(refusal);
            case Judgement.Accepted _ -> new UserRegistrationCheck.Acceptable();
        };
    }

    /**
     * 利用者を登録する（確定。画面の POST から、外側のトランザクションの外で呼ぶ。合流すると、拒否と重複のメールアドレスで外側を
     * rollback-only にし、範囲外の企業では試行を記録できずに例外になる）。
     *
     * @param operator 操作者
     * @param companyId 所属させる企業
     * @param email メールアドレス（形は画面が確かめる）
     * @param displayName 表示名
     * @param roles 付ける役割
     */
    public UserRegistrationOutcome register(
            AuthenticatedActor operator, CompanyId companyId, EmailAddress email, String displayName, Set<Role> roles) {
        UserRegistrationOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> {
                UserRegistrationOutcome result = registerInTransaction(operator, companyId, email, displayName, roles);
                if (!(result instanceof UserRegistrationOutcome.Registered)) {
                    status.setRollbackOnly();
                }
                return result;
            });
        } catch (DuplicateEmailException _) {
            // 同じメールアドレスの同時の登録。先の確かめの後に入った（一意制約で後の 1 つが落ちる）
            return new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.DuplicateEmail());
        }
        if (outcome instanceof UserRegistrationOutcome.Refused(var refusal)
                && refusal instanceof UserRegistrationRefusal.CompanyOutOfScope) {
            recordOutOfScope(operator, companyId);
        }
        return outcome;
    }

    private UserRegistrationOutcome registerInTransaction(
            AuthenticatedActor operator, CompanyId companyId, EmailAddress email, String displayName, Set<Role> roles) {
        return switch (judge(operator, companyId, email, displayName, roles)) {
            case Judgement.Refused(var refusal) -> new UserRegistrationOutcome.Refused(refusal);
            case Judgement.Accepted(User.UserRegistrationApproval approval, String name) -> {
                UtcInstant now = new UtcInstant(clock.instant());
                User registered = User.register(
                        approval, new UserId(UUID.randomUUID()), email, name, unusablePasswordHashes.issue());
                users.addRegistered(registered, operator.userId(), now);
                // 監査の役割は作った利用者から取る（前例 UserRoleChangeService の承認からの取り方）
                auditRecords.append(AuditRecord.userRegistered(
                        operator.userId(), operator.companyId(), registered.id(), registered.roles(), now));
                yield new UserRegistrationOutcome.Registered(registered.id());
            }
        };
    }

    /** 管理範囲の外の企業への登録の試行を、登録とは別のトランザクションで書く（外側に合流すると外側のロールバックで消える）。 */
    private void recordOutOfScope(AuthenticatedActor operator, CompanyId companyId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("範囲外の試行の記録は外側のトランザクションの中では呼べない");
        }
        transactionTemplate.executeWithoutResult(status -> auditRecords.append(AuditRecord.adminScopeDeniedCompany(
                operator.userId(), operator.companyId(), companyId, new UtcInstant(clock.instant()))));
    }

    /** 判定する（操作者 → 企業の有無 → 管理範囲 → 企業の有効性 → 表示名 → 登録の判定 → 重複のメールアドレス）。 */
    private Judgement judge(
            AuthenticatedActor operator, CompanyId companyId, EmailAddress email, String displayName, Set<Role> roles) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return refused(new UserRegistrationRefusal.NotPermitted());
        }
        Optional<Company> company = companies.findById(companyId);
        if (company.isEmpty()) {
            return refused(new UserRegistrationRefusal.CompanyNotFound());
        }
        if (!assignments.scopeOf(operator.userId()).covers(companyId)) {
            return refused(new UserRegistrationRefusal.CompanyOutOfScope());
        }
        if (!company.get().active()) {
            return refused(new UserRegistrationRefusal.InactiveCompany());
        }
        return normalizedDisplayName(displayName)
                .map(name -> decide(company.get(), email, name, roles))
                .orElseGet(() -> refused(new UserRegistrationRefusal.InvalidDisplayName()));
    }

    /** 表示名の後の段。登録の判定と重複のメールアドレスを見る。 */
    private Judgement decide(Company company, EmailAddress email, String name, Set<Role> roles) {
        return switch (User.decideRegistration(company, roles)) {
            case User.UserRegistrationDecision.Refused(UserRegistrationRejection rejection) ->
                refused(new UserRegistrationRefusal.Rejected(rejection));
            case User.UserRegistrationDecision.Approved(User.UserRegistrationApproval approval) ->
                users.findByEmail(email).isPresent()
                        ? refused(new UserRegistrationRefusal.DuplicateEmail())
                        : new Judgement.Accepted(approval, name);
        };
    }

    /** 前後の空白を除いた表示名。空・200 コードポイントを超える・制御文字を含むなら空。 */
    private static Optional<String> normalizedDisplayName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        boolean valid = !name.isEmpty()
                && name.codePointCount(0, name.length()) <= DISPLAY_NAME_MAX
                && name.codePoints().noneMatch(Character::isISOControl);
        return valid ? Optional.of(name) : Optional.empty();
    }

    private static Judgement refused(UserRegistrationRefusal refusal) {
        return new Judgement.Refused(refusal);
    }

    /** 判定の結果（このクラスの中だけで使う）。 */
    private sealed interface Judgement {

        /** 受け付ける。登録の承認と前後の空白を除いた表示名を持つ。 */
        record Accepted(User.UserRegistrationApproval approval, String displayName) implements Judgement {}

        /** 受け付けない。 */
        record Refused(UserRegistrationRefusal refusal) implements Judgement {}
    }
}
