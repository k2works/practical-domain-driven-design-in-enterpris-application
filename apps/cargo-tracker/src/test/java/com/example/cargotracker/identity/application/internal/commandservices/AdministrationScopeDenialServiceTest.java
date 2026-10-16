package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 範囲外の試行の記録のサービス（Bolt 34b）。追記が失敗したら例外にする（画面は 500 にし、404 で隠さない。IA-INV-08）。 */
class AdministrationScopeDenialServiceTest {

    @Test
    void 監査を書けなければ例外になる() {
        AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
        doThrow(new IllegalStateException("監査の表に届かない")).when(auditRecords).append(any());
        AdministrationScopeDenialService service = new AdministrationScopeDenialService(
                auditRecords,
                Clock.fixed(Instant.parse("2026-10-16T09:00:00Z"), ZoneOffset.UTC),
                new ImmediateTransactions());
        AuthenticatedActor admin = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.SYSTEM_ADMIN),
                "管理",
                "A 社");
        UserId target = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> service.recordDenied(admin, target)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 外側のトランザクションの中では呼べない() {
        AdministrationScopeDenialService service = new AdministrationScopeDenialService(
                mock(AuditRecordRepository.class), Clock.systemUTC(), new ImmediateTransactions());
        AuthenticatedActor admin = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.SYSTEM_ADMIN),
                "管理",
                "A 社");
        UserId target = new UserId(UUID.randomUUID());
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.recordDenied(admin, target)).isInstanceOf(IllegalStateException.class);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    /** トランザクションを開かずにそのまま呼ぶ（単体テスト用）。 */
    private static final class ImmediateTransactions extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
