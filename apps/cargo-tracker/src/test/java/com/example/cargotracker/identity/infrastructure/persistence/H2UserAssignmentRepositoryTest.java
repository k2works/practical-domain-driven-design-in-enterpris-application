package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 担当範囲のリポジトリの契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 38e、ADR-007）。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class H2UserAssignmentRepositoryTest extends UserAssignmentRepositoryContract {

    @Autowired
    UserAssignmentRepository assignments;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected UserAssignmentRepository assignments() {
        return assignments;
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
