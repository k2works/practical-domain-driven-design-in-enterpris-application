package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 担当範囲の外の試行の記録のサービス（Bolt 38f の行 8・9。前例 {@link AdministrationScopeDenialServiceTest}）。 */
class AssignedScopeDenialServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");

    private final AuthenticatedActor sales = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(Role.SALES), "営業 一郎", "A 社");

    private static AssignedScopeDenialService serviceWith(AuditRecordRepository auditRecords) {
        return new AssignedScopeDenialService(
                auditRecords, Clock.fixed(NOW, ZoneOffset.UTC), new ImmediateTransactions());
    }

    @Test
    void 担当範囲の外の試行を対象とともに失敗として書く() {
        List<AuditRecord> written = new ArrayList<>();
        AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
        org.mockito.Mockito.doAnswer(call -> written.add(call.getArgument(0)))
                .when(auditRecords)
                .append(any());

        serviceWith(auditRecords).recordDenied(sales, ScopedResourceKind.QUOTATION, "TR-2026-0001/2");

        assertThat(written).singleElement().satisfies(denied -> {
            assertThat(denied.action()).isEqualTo(AuditAction.ASSIGNED_SCOPE_DENIED);
            assertThat(denied.result()).isEqualTo(AuditResult.FAILURE);
            assertThat(denied.reason()).isEqualTo("OUT_OF_SCOPE");
            assertThat(denied.actorUserId()).isEqualTo(sales.userId());
            assertThat(denied.actorCompanyId()).isEqualTo(sales.companyId());
            assertThat(denied.occurredAt().instant()).isEqualTo(NOW);
            assertThat(denied.target().type()).isEqualTo("QUOTATION");
            assertThat(denied.target().id()).isEqualTo("TR-2026-0001/2");
            assertThat(denied.target().version()).isNull();
            assertThat(denied.target().beforeState()).isNull();
            assertThat(denied.target().afterState()).isNull();
        });
    }

    @Test
    void 監査を書けなければ例外になる() {
        // 行 9（画面は 500。404 で隠さない。IA-INV-08）
        AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
        doThrow(new IllegalStateException("監査の表に届かない")).when(auditRecords).append(any());
        AssignedScopeDenialService service = serviceWith(auditRecords);

        assertThatThrownBy(() -> service.recordDenied(sales, ScopedResourceKind.TRANSPORT_REQUEST, "TR-2026-0001"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 外側のトランザクションの中では呼べず何も書かない() {
        // 行 8（外側に合流すると、外側のロールバックで試行の記録も消える）
        AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
        AssignedScopeDenialService service = serviceWith(auditRecords);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.recordDenied(sales, ScopedResourceKind.TRANSPORT_REQUEST, "TR-2026-0001"))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(auditRecords);
    }

    /** トランザクションを開かずにそのまま呼ぶ（単体テスト用）。 */
    private static final class ImmediateTransactions extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
