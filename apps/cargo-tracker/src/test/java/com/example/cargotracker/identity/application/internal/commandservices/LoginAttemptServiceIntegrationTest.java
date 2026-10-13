package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.OverlappingTransactions;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログインの試行のサービスの同時の失敗を、本物の PostgreSQL で確かめる（Bolt 32、IA-INV-03・IA-INV-08、Bolt 30 の議題 2 の③）。
 * 最初の失敗の作成の競合、同時の失敗の両方の監査記録、同時の 5 回目でロックの監査が 1 行だけになること（T-100）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginAttemptServiceIntegrationTest {

    @Autowired
    LoginAttemptService service;

    @Autowired
    LoginFailureRepository failures;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private UserId newUser() {
        return transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
    }

    private CompanyId companyOf(UserId user) {
        return users.findById(user).orElseThrow().companyId();
    }

    private int auditCount(UserId user, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE actor_user_id = ? AND action = ?",
                Integer.class,
                user.value(),
                action);
    }

    @Test
    void 最初の失敗の記録の作成が重なっても後の作成は先を待って違反を受け数えて監査記録を書く() {
        // 議題 2 の③: 記録の作成の一意制約の違反をトランザクションの外で受ける。先の作成のコミットを止め、後の作成が一意索引で
        // 待つことを確かめてから先をコミットする（開始のラッチだけでは競合した保証がない。テスターの指摘）
        UserId user = newUser();
        CompanyId company = companyOf(user);

        OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate,
                () -> {
                    failures.prepare(user, new UtcInstant(Instant.now()));
                    return Boolean.TRUE;
                },
                () -> {
                    service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                    return Boolean.TRUE;
                });

        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(1);
        assertThat(auditCount(user, "LOGIN_FAILED")).isEqualTo(1);
    }

    @Test
    void 重なった5回目の失敗はロックの監査記録を1行だけ書き後の失敗は理由LOCKEDで数えない() {
        UserId user = newUser();
        CompanyId company = companyOf(user);
        for (int i = 0; i < 4; i++) {
            service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
        }

        // 外側のトランザクションの中で呼ぶ（本番と違う）。記録はもうあり、作成はサービスが外側から切り離すので害はない
        OverlappingTransactions.run(
                transactionTemplate,
                () -> {
                    service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                    return Boolean.TRUE;
                },
                () -> {
                    service.recordFailure(user, company, AuthenticationRejection.BAD_CREDENTIALS);
                    return Boolean.TRUE;
                });

        assertThat(failures.findByUserId(user).consecutiveFailures()).isEqualTo(5);
        assertThat(auditCount(user, "ACCOUNT_LOCKED")).as("この失敗でロックしたときだけ").isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT reason FROM identity.audit_record WHERE actor_user_id = ? AND action = 'LOGIN_FAILED'",
                        String.class,
                        user.value()))
                .containsExactlyInAnyOrder(
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "LOCKED");
    }
}
