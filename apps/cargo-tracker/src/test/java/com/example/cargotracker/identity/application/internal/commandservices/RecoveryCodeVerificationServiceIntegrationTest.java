package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.OverlappingTransactions;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 同じ回復コードの同時の使用のうち 1 つだけが通ることを、本物の bcrypt と PostgreSQL で確かめる（Bolt 31c、IA-INV-11）。
 * 先の使用のコミットを止め、後の使用がその行ロックを待っていることを確かめてから放す（T-100）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecoveryCodeVerificationServiceIntegrationTest {

    private static final String PLAIN = "1a2b-3c4d";

    @Autowired
    RecoveryCodeVerificationService service;

    @Autowired
    RecoveryCodeRepository codes;

    @Autowired
    RecoveryCodeHasher hasher;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 重なった同じ回復コードの使用は先の1つだけが通り後は拒む() {
        UserId user = transactionTemplate.execute(status -> AuthenticationFactorFixtures.newUser(companies, users));
        CompanyId company = users.findById(user).orElseThrow().companyId();
        UtcInstant now = new UtcInstant(clock.instant());
        List<RecoveryCode> issued = IntStream.range(0, RecoveryCode.CODES_PER_USER)
                .mapToObj(i -> RecoveryCode.issue(user, i == 0 ? PLAIN : String.format("0000-%04x", i), hasher, now))
                .toList();
        transactionTemplate.executeWithoutResult(status -> codes.replaceAll(user, issued));

        List<RecoveryCodeVerification> results = OverlappingTransactions.run(
                transactionTemplate,
                () -> service.verify(user, company, PLAIN),
                () -> service.verify(user, company, PLAIN));

        assertThat(results).containsExactly(RecoveryCodeVerification.ACCEPTED, RecoveryCodeVerification.REJECTED);
        assertThat(codes.findUnusedByUserId(user)).hasSize(RecoveryCode.CODES_PER_USER - 1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE actor_user_id = ? AND action = 'RECOVERY_CODE_USED'",
                        Integer.class,
                        user.value()))
                .as("後になった側は監査記録を書かない")
                .isEqualTo(1);
    }
}
