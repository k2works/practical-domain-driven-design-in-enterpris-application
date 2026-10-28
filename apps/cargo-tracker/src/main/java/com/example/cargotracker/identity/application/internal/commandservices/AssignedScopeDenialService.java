package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 担当範囲の外の試行の記録のサービス。業務担当者の担当企業の外の確定の操作を、監査記録 {@code ASSIGNED_SCOPE_DENIED} に書く（Bolt 38f。
 * US-16 AC4、IA-INV-08、ADR-019）。前例は管理範囲の外の試行の {@link AdministrationScopeDenialService}。共有カーネルのポートの実装
 * （{@code interfaces.api.internal} のアダプター）からだけ呼ばれる。業務のトランザクションの外で呼ばれ、自分のトランザクションで書く
 * （外側のトランザクションの中では呼べない）。追記が失敗したら例外にする（画面は 500）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class AssignedScopeDenialService {

    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AssignedScopeDenialService(
            AuditRecordRepository auditRecords, Clock clock, TransactionTemplate transactionTemplate) {
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 担当範囲の外の試行を書く。
     *
     * @param operator 操作者（業務担当者）
     * @param kind 対象の種類
     * @param resourceId 対象の ID
     */
    public void recordDenied(AuthenticatedActor operator, ScopedResourceKind kind, String resourceId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 外側に合流すると、外側のロールバックで試行の記録も消える
            throw new IllegalStateException("担当範囲の外の試行の記録は外側のトランザクションの中では呼べない");
        }
        transactionTemplate.executeWithoutResult(status -> auditRecords.append(AuditRecord.assignedScopeDenied(
                operator.userId(), operator.companyId(), kind.name(), resourceId, new UtcInstant(clock.instant()))));
    }
}
