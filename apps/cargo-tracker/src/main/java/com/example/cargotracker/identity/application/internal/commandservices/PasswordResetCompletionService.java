package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CommonPasswordList;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenHasher;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.rules.PasswordPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordPolicyViolation;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再設定の完了のサービス。password の再設定のリンクの確かめと A-06 の設定（Bolt 33a、US-18 AC8、SEC-04・SEC-05・SEC-08、IA-INV-12）。リンクのトークンを確かめ（{@link #open}・
 * {@link #isUsable}）、A-06 で新しい password を設定する（{@link #complete}）。
 *
 * <p>設定は、方針を確かめてから（誤りではトークンを使わない）、1 つのトランザクションで、トークンを条件付きで使い、password を変え、同じ利用者の
 * 未使用のトークンを無効にし、監査を書く。利用停止・企業の無効は、どれかを示さずに無効とする。確定の後に、その利用者の session をすべて
 * 失効させる（Spring Session は自分のトランザクションで動くため。消せなくても password は変わっているので、警告のログにとどめる）。
 * ロックと失敗の数は変えない（SEC-02）。トークンの値はログに出さない。
 */
@Service
public class PasswordResetCompletionService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetCompletionService.class);

    private final PasswordResetTokenRepository tokens;
    private final PasswordResetTokenHasher tokenHasher;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordHasher passwordHasher;
    private final CommonPasswordList commonPasswords;
    private final UserSessionRevoker sessions;
    private final AuditRecordRepository auditRecords;
    private final PasswordPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("java:S107") // 再設定に要るポートと方針をそろえて受ける
    public PasswordResetCompletionService(
            PasswordResetTokenRepository tokens,
            PasswordResetTokenHasher tokenHasher,
            UserRepository users,
            CompanyRepository companies,
            PasswordHasher passwordHasher,
            CommonPasswordList commonPasswords,
            UserSessionRevoker sessions,
            AuditRecordRepository auditRecords,
            PasswordPolicy policy,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
        this.users = users;
        this.companies = companies;
        this.passwordHasher = passwordHasher;
        this.commonPasswords = commonPasswords;
        this.sessions = sessions;
        this.auditRecords = auditRecords;
        this.policy = policy;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * リンクのトークンを確かめる。使えればトークンの ID を返す（画面は ID だけを session に置く）。使えなければ空。
     *
     * @param token リンクに入っていた値（平文）
     */
    public Optional<UUID> open(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        UtcInstant now = now();
        return tokens.findByTokenHash(tokenHasher.hash(token))
                .filter(found -> found.isUsable(now))
                .map(PasswordResetToken::id);
    }

    /** トークンがまだ使えるか（A-06 を開いたとき）。 */
    public boolean isUsable(UUID tokenId) {
        UtcInstant now = now();
        return tokens.findById(tokenId).filter(found -> found.isUsable(now)).isPresent();
    }

    /**
     * 新しい password を設定する。
     *
     * @param tokenId リンクを開いたときに確かめたトークンの ID
     * @param newPassword 新しい password（確認の欄との一致は画面が確かめる）
     */
    public PasswordResetCompletion complete(UUID tokenId, String newPassword) {
        Optional<PasswordPolicyViolation> violation =
                policy.violation(newPassword, commonPasswords.contains(newPassword));
        if (violation.isPresent()) {
            return new PasswordResetCompletion.PolicyViolated(violation.get());
        }
        Optional<EmailAddress> changed = Optional.ofNullable(transactionTemplate.execute(status -> {
            Optional<EmailAddress> result = changePassword(tokenId, newPassword);
            if (result.isEmpty()) {
                status.setRollbackOnly();
            }
            return result.orElse(null);
        }));
        if (changed.isEmpty()) {
            return new PasswordResetCompletion.Invalid();
        }
        revokeSessions(tokenId, changed.get());
        return new PasswordResetCompletion.Completed();
    }

    /** トランザクションの中で、トークンを使って password を変える。使えなければ空。 */
    private Optional<EmailAddress> changePassword(UUID tokenId, String newPassword) {
        UtcInstant now = now();
        Optional<PasswordResetToken> token = tokens.findById(tokenId);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> user = users.findById(token.get().userId());
        Optional<Company> company = user.flatMap(found -> companies.findById(found.companyId()));
        if (user.isEmpty()
                || company.isEmpty()
                || user.get().authenticationRejection(company.get()).isPresent()
                || !tokens.markUsed(tokenId, now)) {
            return Optional.empty();
        }
        User changed = user.get().changePassword(passwordHasher.hash(newPassword));
        if (!users.changePassword(changed)) {
            throw new IllegalStateException("password を書けなかった: " + changed.id());
        }
        tokens.invalidateUnused(changed.id(), now);
        auditRecords.append(
                AuditRecord.passwordResetCompleted(changed.id(), company.get().id(), now));
        return Optional.of(changed.email());
    }

    private void revokeSessions(UUID tokenId, EmailAddress email) {
        try {
            int revoked = sessions.revokeAll(email);
            LOG.info("password の再設定の後に利用者の session を失効させた: トークン {}、件数 {}", tokenId, revoked);
        } catch (RuntimeException e) {
            LOG.warn("password の再設定の後に利用者の session を失効させられなかった（password は変わっている）: トークン {}", tokenId, e);
        }
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant());
    }
}
