package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 範囲外の試行の記録のサービス。管理範囲外の利用者への試行を監査記録 {@code ADMIN_SCOPE_DENIED} に書く（Bolt 34b。US-16 AC2、
 * IA-INV-08・IA-INV-14）。S-20 の controller だけが、範囲外と分かったときに 1 回の request につき 1 回呼ぶ。役割の変更のトランザクション
 * の外で呼ばれ、自分のトランザクションで書く（入れ子にしない）。追記が失敗したら例外にする（画面は 500。404 にしない）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class AdministrationScopeDenialService {

    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AdministrationScopeDenialService(
            AuditRecordRepository auditRecords, Clock clock, TransactionTemplate transactionTemplate) {
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 範囲外の試行を書く。
     *
     * @param operator 操作者（システム管理者）
     * @param target 管理範囲外の利用者
     */
    public void recordDenied(AuthenticatedActor operator, UserId target) {
        transactionTemplate.executeWithoutResult(status -> auditRecords.append(AuditRecord.adminScopeDenied(
                operator.userId(), operator.companyId(), target, new UtcInstant(clock.instant()))));
    }
}
