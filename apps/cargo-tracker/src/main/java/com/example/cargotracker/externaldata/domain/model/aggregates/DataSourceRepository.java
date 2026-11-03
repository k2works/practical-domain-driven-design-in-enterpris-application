package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/**
 * 情報源のリポジトリ。情報源を ID で引き、承認済みの情報源を数えるドメインのポート（Bolt 39b）。読み取りだけを持つ（情報源の選定・承認は
 * W10 の運用タスク）。外部データは集約を持たないが、ポートは {@code domain.model.aggregates} に置く規則（CLAUDE.md、ADR-017）に従う。
 */
public interface DataSourceRepository {

    /** ID で情報源を引く。ないときは空。 */
    Optional<DataSource> findById(UUID id);

    /** 承認済み（{@link DataSource.ApprovalStatus#APPROVED}）の情報源の件数。候補と停止中は数えない。 */
    int countApproved();
}
