package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 利用者の立場のリポジトリの契約を PostgreSQL 18 で確かめる（Bolt 32c、ADR-007）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisUserStandingRepositoryIntegrationTest extends UserStandingRepositoryContract {

    @Autowired
    UserStandingRepository standings;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected UserStandingRepository standings() {
        return standings;
    }

    @Override
    protected CompanyRepository companies() {
        return companies;
    }

    @Override
    protected UserRepository users() {
        return users;
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
