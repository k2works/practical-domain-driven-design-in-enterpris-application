package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.rules.SessionPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.EnrollingUser;
import com.example.cargotracker.identity.domain.model.valueobjects.SessionId;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpMatch;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 認証アプリの再登録（Bolt 33b、US-18 AC9、SEC-10、IA-INV-10・11）。回復コードでログインした利用者が、新しい秘密で認証アプリを
 * 登録し直す。
 *
 * <p>完了は、15 分の上限と新しいコードを確かめてから、1 つのトランザクションで、資格を置き換え（再登録の前に読んだ秘密のままのときだけ。
 * 同時の 2 つの再登録と二度押しは 1 つだけが通る）、回復コード 10 個を発行し直し、監査を書く。確定の後に、その利用者の今の session の
 * ほかを失効させる（Spring Session は自分のトランザクションで動くため。消せなくても資格は変わっているので、警告のログにとどめる）。
 * 外側のトランザクションの中では呼べない（参加すると外側の確定の前に session を消してしまう。ADR-012）。利用者を読んでメールアドレスと
 * 企業を得る（主体の型は変えない。Bolt 33b の開始準備の決定 7）。資格がない・利用停止・企業の無効は、どれかを示さずに再登録できない
 * とする。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpReEnrollmentService {

    private static final Logger LOG = LoggerFactory.getLogger(TotpReEnrollmentService.class);

    private final UserRepository users;
    private final CompanyRepository companies;
    private final TotpCredentialRepository credentials;
    private final RecoveryCodeRepository recoveryCodes;
    private final AuditRecordRepository auditRecords;
    private final TotpProvisioningStarter starter;
    private final RecoveryCodeGenerator recoveryCodeGenerator;
    private final RecoveryCodeHasher hasher;
    private final TotpCodeGenerator generator;
    private final UserSessionRevoker sessions;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("java:S107") // 再登録に要るポートをそろえて受ける（前例のアプリケーションサービスと同じく、組み立てで渡す）
    public TotpReEnrollmentService(
            UserRepository users,
            CompanyRepository companies,
            TotpCredentialRepository credentials,
            RecoveryCodeRepository recoveryCodes,
            AuditRecordRepository auditRecords,
            TotpSecretIssuer issuer,
            RecoveryCodeGenerator recoveryCodeGenerator,
            RecoveryCodeHasher hasher,
            TotpCodeGenerator generator,
            UserSessionRevoker sessions,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.companies = companies;
        this.credentials = credentials;
        this.recoveryCodes = recoveryCodes;
        this.auditRecords = auditRecords;
        this.starter = new TotpProvisioningStarter(issuer);
        this.recoveryCodeGenerator = recoveryCodeGenerator;
        this.hasher = hasher;
        this.generator = generator;
        this.sessions = sessions;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 再登録を始める。再登録の途中の暗号文を受けたら、同じ利用者の暗号文として使える限りそれを使い続け（再読み込みで秘密を変えない）、
     * 使えなければ新しく発行する。
     *
     * @param pending 再登録の途中の暗号文（session に置いた値）
     */
    public ReEnrollmentStart begin(UserId userId, Optional<EncryptedSecret> pending) {
        Optional<EnrollingUser> user = reEnrollable(userId);
        if (user.isEmpty() || credentials.findByUserId(userId).isEmpty()) {
            return new ReEnrollmentStart.Unavailable();
        }
        TotpProvisioningStarter.Started started =
                starter.start(userId, pending, user.get().email().value());
        return new ReEnrollmentStart.Started(started.secret(), started.provisioning());
    }

    /**
     * 新しいコードで確かめて登録し直す。
     *
     * @param secret 再登録の途中の暗号文
     * @param startedAt 再登録を始めた時刻
     * @param currentSessionId 今の session の ID（失効させずに残す）
     */
    public ReEnrollmentResult complete(
            UserId userId, EncryptedSecret secret, String code, UtcInstant startedAt, SessionId currentSessionId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("認証アプリの再登録は外側のトランザクションの中では呼べない（確定の後に session を失効させるため）");
        }
        Optional<EnrollingUser> user = reEnrollable(userId);
        Optional<TotpCredential> current = credentials.findByUserId(userId);
        if (user.isEmpty() || current.isEmpty()) {
            return new ReEnrollmentResult.Unavailable();
        }
        if (current.get().secret().equals(secret)) {
            // 二度押し・別のタブ。先の再登録で示した回復コードを無効にしない
            return new ReEnrollmentResult.Superseded();
        }
        UtcInstant now = new UtcInstant(clock.instant());
        if (SessionPolicy.isExpired(startedAt, now, SessionPolicy.RE_ENROLLMENT_LIFETIME)) {
            return new ReEnrollmentResult.Expired();
        }
        TotpMatch match;
        try {
            match = TotpCredential.enroll(userId, secret, now).match(code, TotpCredential.stepAt(now), generator);
        } catch (UnreadableCredentialException _) {
            return new ReEnrollmentResult.SecretUnusable();
        }
        return switch (match) {
            case TotpMatch.Matched(long step) ->
                replace(user.get(), current.get(), secret, step, now, currentSessionId);
            // 新しい秘密の資格はどの刻みも使っていないので、再利用にはならない
            case TotpMatch.Reused _, TotpMatch.Mismatch _ -> new ReEnrollmentResult.CodeRejected();
        };
    }

    private ReEnrollmentResult replace(
            EnrollingUser user,
            TotpCredential current,
            EncryptedSecret secret,
            long step,
            UtcInstant now,
            SessionId currentSessionId) {
        UserId owner = user.userId();
        // 回復コードの生成とハッシュ（bcrypt 10 回）は書き込みでないので、資格の行ロックを取る前に済ませる（登録と同じ）
        List<String> plainCodes = recoveryCodeGenerator.generate();
        List<RecoveryCode> issued = RecoveryCode.issueSet(owner, plainCodes, hasher, now);
        Boolean replaced = transactionTemplate.execute(status -> {
            // 資格の置き換えを先に行い、同時の再登録では回復コードを置き換える前に後の再登録を止める
            if (!credentials.replace(current.reEnroll(secret, step, now), current.secret())) {
                status.setRollbackOnly();
                return false;
            }
            recoveryCodes.replaceAll(owner, issued);
            auditRecords.append(AuditRecord.totpReEnrolled(owner, user.companyId(), now));
            return true;
        });
        if (!Boolean.TRUE.equals(replaced)) {
            return new ReEnrollmentResult.Superseded();
        }
        revokeOthers(user, currentSessionId);
        return new ReEnrollmentResult.ReEnrolled(plainCodes);
    }

    private void revokeOthers(EnrollingUser user, SessionId currentSessionId) {
        try {
            int revoked = sessions.revokeOthers(user.email(), currentSessionId);
            LOG.info("認証アプリの再登録の後に利用者のほかの session を失効させた: 利用者 {}、件数 {}", user.userId(), revoked);
        } catch (RuntimeException e) {
            LOG.warn("認証アプリの再登録の後に利用者のほかの session を失効させられなかった（資格は変わっている）: 利用者 {}", user.userId(), e);
        }
    }

    /** 再登録できる利用者（利用停止・企業の無効でない）を読む。 */
    private Optional<EnrollingUser> reEnrollable(UserId userId) {
        Optional<User> user = users.findById(userId);
        Optional<Company> company = user.flatMap(found -> companies.findById(found.companyId()));
        if (user.isEmpty()
                || company.isEmpty()
                || user.get().authenticationRejection(company.get()).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(
                new EnrollingUser(userId, user.get().companyId(), user.get().email()));
    }
}
