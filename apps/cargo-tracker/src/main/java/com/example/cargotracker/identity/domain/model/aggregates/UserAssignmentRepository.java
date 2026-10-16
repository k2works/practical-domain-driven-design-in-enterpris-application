package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;

/**
 * 担当範囲のリポジトリ（送信ポート。Bolt 34b）。システム管理者に割り当てた企業（`user_assignment` の `COMPANY` の行）を読む。
 * 割り当ては運用の手順で行い、アプリは書かない（割り当ての画面は W6。2026-10-16、human:kakimomokuri）。
 */
public interface UserAssignmentRepository {

    /** システム管理者に管理範囲として割り当てた企業。なければ空。 */
    Set<CompanyId> managedCompanies(UserId admin);
}
