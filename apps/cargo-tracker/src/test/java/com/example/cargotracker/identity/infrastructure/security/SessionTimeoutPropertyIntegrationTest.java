package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 無操作の期限の設定の鍵（spring.session.timeout）が Spring Session JDBC の session に効くことを、既定（30 分）と
 * 違う値で確かめる（Bolt 14 レビュー。既定の 30 分のままでは、鍵を誤っても境界のテストが通ってしまう）。
 */
@SpringBootTest(properties = "spring.session.timeout=10m")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionTimeoutPropertyIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 設定した無操作の期限がsessionに入る() throws Exception {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
        String email = "timeout-" + UUID.randomUUID() + "@example.com";
        users.add(User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                "利用者",
                passwordEncoder.encode("pw"),
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER)));

        mvc.perform(
                post("/login").param("username", email).param("password", "pw").with(csrf()));

        assertThat(jdbc.queryForObject(
                        "SELECT max_inactive_interval FROM platform.spring_session WHERE principal_name = ?",
                        Integer.class,
                        email))
                .isEqualTo(600);
    }
}
