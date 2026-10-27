package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 停止の記録の照会の契約を、ローカル起動と同じ dev プロファイルの H2 で確かめる（Bolt 38c2、ADR-007）。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class H2AuditRecordSuspensionTest extends AuditRecordSuspensionContract {

    @Autowired
    AuditRecordRepository auditRecords;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected AuditRecordRepository auditRecords() {
        return auditRecords;
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
