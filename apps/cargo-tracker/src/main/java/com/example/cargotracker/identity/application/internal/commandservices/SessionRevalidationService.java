package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserStanding;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * session の確かめのサービス（Bolt 32c、US-18 AC6、IA-INV-04、ADR-012 の見直しの 3）。認証済みの request ごとに、利用者・役割・
 * 所属企業を 1 回で読み、ログインと同じドメインの規則（{@link User#authenticationRejection}）で判定する。拒否なら
 * {@code SESSION_REVOKED} を 1 つのトランザクションで書いて理由を返し、拒否でなければ今の役割・表示名・企業名の主体を返す。
 * 役割が変わっただけのときは記録しない。認証の入口のフィルターから呼ぶので、層の規則 D-5 の認証の例外の範囲（入口から
 * 呼べるのは commandservices）に置く。読み出し・監査の誤りは投げ、入口が session を捨てて 500 にする（fail-closed）。
 */
@Service
public class SessionRevalidationService {

    private final UserStandingRepository standings;
    private final AuditRecordRepository auditRecords;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public SessionRevalidationService(
            UserStandingRepository standings,
            AuditRecordRepository auditRecords,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.standings = standings;
        this.auditRecords = auditRecords;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * session の主体を今の値で確かめる。
     *
     * @param userId session の主体の利用者
     * @param companyId session の主体の企業（取消しの監査記録の操作者）
     */
    public SessionStanding revalidate(UserId userId, CompanyId companyId) {
        Optional<UserStanding> found = standings.findById(userId);
        if (found.isEmpty()) {
            return revoke(userId, companyId, AuthenticationRejection.UNKNOWN_USER);
        }
        UserStanding standing = found.get();
        User user = standing.user();
        Optional<AuthenticationRejection> rejection = user.authenticationRejection(standing.company());
        if (rejection.isPresent()) {
            return revoke(userId, companyId, rejection.get());
        }
        return new SessionStanding.Current(
                new AuthenticatedActor(
                        user.id(),
                        standing.company().id(),
                        user.roles(),
                        user.displayName(),
                        standing.company().name()),
                user.email());
    }

    /**
     * 取消しを記録する。操作者は session の主体（利用者が消えていても追跡できるように）。利用者は企業を移らない前提で、session の
     * 企業 ID は読み出した利用者の企業と同じ（企業の移動は利用者の管理（US-16）で扱うときに見直す）。
     */
    private SessionStanding revoke(UserId userId, CompanyId companyId, AuthenticationRejection reason) {
        AuditRecord revoked = AuditRecord.sessionRevoked(userId, companyId, reason, new UtcInstant(clock.instant()));
        transactionTemplate.executeWithoutResult(status -> auditRecords.append(revoked));
        return new SessionStanding.Revoked(reason);
    }
}
