package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.DuplicateTotpEnrollmentException;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordVerifiedUser;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpMatch;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TOTP の登録（Bolt 31b、US-18 AC7、SEC-09、IA-INV-10・11）。登録を始めると秘密を発行して表示の値を返し、最初のコードで確かめて
 * から、1 つのトランザクションで TOTP の資格と回復コード 10 個（ハッシュ）と監査記録を保存し、回復コードの平文を一度だけ返す。
 * 登録は TOTP の要素を与えない（登録の後は A-02 で TOTP を入れる。Bolt 31b の確認ポイント 2）。同時の 2 つの登録は TOTP の資格の
 * 主キーで 1 つに絞る。後の登録は主キーの違反で自分のトランザクションをロールバックし、トランザクションの外側でその違反を受けて
 * 「登録済み」を返す（ADR-007 の「一意制約の違反は同じトランザクションで捕まえない」を守りつつ、結果を 1 つの経路にする。
 * Bolt 31b の開発レビュー）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class TotpEnrollmentService {

    private final TotpCredentialRepository credentials;
    private final RecoveryCodeRepository recoveryCodes;
    private final AuditRecordRepository auditRecords;
    private final TotpSecretIssuer issuer;
    private final RecoveryCodeGenerator recoveryCodeGenerator;
    private final RecoveryCodeHasher hasher;
    private final TotpCodeGenerator generator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("java:S107") // 登録に要るポートをそろえて受ける（前例のアプリケーションサービスと同じく、組み立てで渡す）
    public TotpEnrollmentService(
            TotpCredentialRepository credentials,
            RecoveryCodeRepository recoveryCodes,
            AuditRecordRepository auditRecords,
            TotpSecretIssuer issuer,
            RecoveryCodeGenerator recoveryCodeGenerator,
            RecoveryCodeHasher hasher,
            TotpCodeGenerator generator,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.credentials = credentials;
        this.recoveryCodes = recoveryCodes;
        this.auditRecords = auditRecords;
        this.issuer = issuer;
        this.recoveryCodeGenerator = recoveryCodeGenerator;
        this.hasher = hasher;
        this.generator = generator;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 登録を始める。登録済みなら拒む。登録の途中の暗号文を受けたら、同じ利用者の暗号文として使える限りそれを使い続け（再読み込みや
     * 2 つのタブで秘密を変えない）、使えなければ新しく発行する。
     *
     * @param user password を確かめた利用者（メールアドレスを認証アプリに示す名前にする）
     * @param pending 登録の途中の暗号文（session に置いた値）
     */
    public EnrollmentStart begin(PasswordVerifiedUser user, Optional<EncryptedSecret> pending) {
        UserId owner = user.userId();
        String accountName = user.email().value();
        if (credentials.findByUserId(owner).isPresent()) {
            return new EnrollmentStart.AlreadyEnrolled();
        }
        if (pending.isPresent()) {
            Optional<TotpProvisioning> reused = provisioningOf(owner, pending.get(), accountName);
            if (reused.isPresent()) {
                return new EnrollmentStart.Started(pending.get(), reused.get());
            }
        }
        EncryptedSecret secret = issuer.issue(owner);
        return new EnrollmentStart.Started(secret, issuer.provisioning(owner, secret, accountName));
    }

    private Optional<TotpProvisioning> provisioningOf(UserId owner, EncryptedSecret secret, String accountName) {
        try {
            return Optional.of(issuer.provisioning(owner, secret, accountName));
        } catch (UnreadableCredentialException _) {
            // 別の利用者の暗号文・壊れた暗号文・知らない鍵の版は使い続けず、新しく発行する（失敗へ倒す。T-42）
            return Optional.empty();
        }
    }

    /**
     * 最初のコードで確かめて登録する。誤ったコード・使えない暗号文では何も保存しない。同時の登録で後になったときも「登録済み」を返す。
     */
    public EnrollmentResult complete(PasswordVerifiedUser user, EncryptedSecret secret, String code) {
        try {
            return transactionTemplate.execute(status -> completeInTransaction(user, secret, code));
        } catch (DuplicateTotpEnrollmentException _) {
            // 先の登録が通っている（主キーの違反で、このトランザクションはロールバックした）
            return new EnrollmentResult.AlreadyEnrolled();
        }
    }

    private EnrollmentResult completeInTransaction(PasswordVerifiedUser user, EncryptedSecret secret, String code) {
        UserId owner = user.userId();
        if (credentials.findByUserId(owner).isPresent()) {
            return new EnrollmentResult.AlreadyEnrolled();
        }
        UtcInstant now = new UtcInstant(clock.instant());
        TotpCredential credential = TotpCredential.enroll(owner, secret, now);
        TotpMatch matched;
        try {
            matched = credential.match(code, TotpCredential.stepAt(now), generator);
        } catch (UnreadableCredentialException _) {
            return new EnrollmentResult.SecretUnusable();
        }
        if (!(matched instanceof TotpMatch.Matched)) {
            // 登録したばかりの資格はどの刻みも使っていないので、再利用にはならない
            return new EnrollmentResult.CodeRejected();
        }
        // 回復コードの生成とハッシュ（bcrypt 10 回）は書き込みでないので、主キーの行ロックを取る前に済ませる（同時の登録の相手を
        // 待たせる時間を短くする。Bolt 31b の開発レビュー）
        List<String> plainCodes = recoveryCodeGenerator.generate();
        List<RecoveryCode> issued = RecoveryCode.requireIssuedSet(
                owner,
                plainCodes.stream()
                        .map(plain -> RecoveryCode.issue(owner, plain, hasher, now))
                        .toList());
        // 資格の追加（主キー）を先に行い、同時の登録では回復コードを置き換える前に後の登録を止める（議題 2 の①）
        credentials.add(credential);
        recoveryCodes.replaceAll(owner, issued);
        auditRecords.append(AuditRecord.totpEnrolled(owner, user.companyId(), now));
        return new EnrollmentResult.Enrolled(plainCodes);
    }
}
