package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.NormalizedRecoveryCode;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 回復コードによるログインの確かめ（Bolt 31c、US-18 AC9 のログインの分、SEC-09、IA-INV-11、ADR-011）。A-02 で TOTP の代わりに
 * 回復コードを 1 つ使う。入力をドメインの形に直し、未使用のコードのすべてと照合し、一致したコードを条件付きの更新で使う。
 * 照合（最大 10 回の bcrypt）はトランザクションの外で行い、使用と監査記録（{@code RECOVERY_CODE_USED}）だけを 1 つの
 * トランザクションにする（DB の接続を照合の間持たない。Bolt 31b の開発レビュー）。同時の使用は条件付きの更新で 1 つだけが通る。
 * 回復コードは TOTP の登録と一緒にしか発行されないので、TOTP を登録していない利用者は回復コードがなく拒まれる。
 */
@Service
public class RecoveryCodeVerificationService {

    private final RecoveryCodeRepository codes;
    private final AuditRecordRepository auditRecords;
    private final RecoveryCodeHasher hasher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RecoveryCodeVerificationService(
            RecoveryCodeRepository codes,
            AuditRecordRepository auditRecords,
            RecoveryCodeHasher hasher,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.codes = codes;
        this.auditRecords = auditRecords;
        this.hasher = hasher;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /** 利用者の入れた回復コードを確かめ、通ればそのコードを使う。拒否の種類は分けない（AC2）。 */
    public RecoveryCodeVerification verify(UserId userId, CompanyId companyId, String code) {
        Optional<NormalizedRecoveryCode> normalized = RecoveryCode.normalize(code);
        if (normalized.isEmpty()) {
            return RecoveryCodeVerification.REJECTED;
        }
        Optional<RecoveryCode> matched = codes.findUnusedByUserId(userId).stream()
                .filter(candidate -> candidate.matches(normalized.get(), hasher))
                .findFirst();
        if (matched.isEmpty()) {
            return RecoveryCodeVerification.REJECTED;
        }
        UtcInstant now = new UtcInstant(clock.instant());
        Boolean used = transactionTemplate.execute(status -> {
            if (!codes.use(userId, matched.get().id(), now)) {
                // 同時の使用で後になった（先の使用が通っている）
                return false;
            }
            auditRecords.append(AuditRecord.recoveryCodeUsed(userId, companyId, now));
            return true;
        });
        return Boolean.TRUE.equals(used) ? RecoveryCodeVerification.ACCEPTED : RecoveryCodeVerification.REJECTED;
    }
}
