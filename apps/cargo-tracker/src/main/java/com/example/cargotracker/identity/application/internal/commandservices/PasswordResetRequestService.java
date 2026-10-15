package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.events.PasswordResetRequested;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 再設定の依頼のサービス（Bolt 33、US-18 AC8、SEC-08、ADR-017）。A-05 の依頼を受け、利用者をメールアドレスで探して、ログインと同じ
 * ドメインの規則（{@link User#authenticationRejection}）で有効かを判定する。依頼を監査記録に同期で書き（IA-INV-08）、有効なら
 * DE-23（{@link PasswordResetRequested}）を同じトランザクションで出す。トークンの発行とメールの送信はコミットの後のイベントハンドラーが
 * 行うので、応答はどの場合も同じで、発行も送信も待たない（登録の有無を示さない）。
 */
@Service
public class PasswordResetRequestService {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final AuditRecordRepository auditRecords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PasswordResetRequestService(
            UserRepository users,
            CompanyRepository companies,
            AuditRecordRepository auditRecords,
            ApplicationEventPublisher events,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.users = users;
        this.companies = companies;
        this.auditRecords = auditRecords;
        this.events = events;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * password の再設定を依頼する。結果を返さない（呼ぶ側は登録の有無にかかわらず同じ応答を返す）。
     *
     * @param email 入力されたメールアドレス（正規化済み）
     */
    public void request(EmailAddress email) {
        transactionTemplate.executeWithoutResult(status -> {
            UtcInstant now = new UtcInstant(clock.instant());
            Optional<User> found = users.findByEmail(email);
            if (found.isEmpty()) {
                auditRecords.append(AuditRecord.passwordResetRequested(
                        null, null, Optional.of(AuthenticationRejection.UNKNOWN_USER), now));
                return;
            }
            User user = found.get();
            Company company = companies
                    .findById(user.companyId())
                    .orElseThrow(() -> new IllegalStateException("利用者の所属企業がない: " + user.id()));
            Optional<AuthenticationRejection> rejection = user.authenticationRejection(company);
            auditRecords.append(AuditRecord.passwordResetRequested(user.id(), company.id(), rejection, now));
            if (rejection.isEmpty()) {
                events.publishEvent(new PasswordResetRequested(UUID.randomUUID(), user.id(), now));
            }
        });
    }
}
