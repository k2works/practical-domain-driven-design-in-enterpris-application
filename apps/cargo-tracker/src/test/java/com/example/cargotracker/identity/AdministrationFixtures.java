package com.example.cargotracker.identity;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * システム管理者の管理範囲をテストの準備として入れる補助（Bolt 34b）。割り当ては運用の手順で行い、アプリは書かない（画面は W6）ので、
 * テストは表へ直接入れる。
 */
public final class AdministrationFixtures {

    private AdministrationFixtures() {}

    /** システム管理者に企業を管理範囲として割り当てる。 */
    public static void assignCompany(JdbcTemplate jdbc, UserId admin, CompanyId company) {
        jdbc.update(
                "INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_at)"
                        + " VALUES (?, 'COMPANY', ?, CURRENT_TIMESTAMP)",
                admin.value(),
                company.value());
    }
}
