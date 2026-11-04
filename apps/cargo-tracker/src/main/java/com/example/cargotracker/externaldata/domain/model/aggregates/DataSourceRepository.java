package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 情報源のリポジトリ。情報源を ID で引き、承認済みの情報源を数えて並べるドメインのポート（Bolt 39b。並べるのは Bolt 39c）。読み取りだけを
 * 持つ（情報源の選定・承認は W10 の運用タスク）。外部データは集約を持たないが、ポートは {@code domain.model.aggregates} に置く規則
 * （CLAUDE.md、ADR-017）に従う。
 */
public interface DataSourceRepository {

    /** ID で情報源を引く。ないときは空。 */
    Optional<DataSource> findById(UUID id);

    /** 承認済み（{@link DataSource.ApprovalStatus#APPROVED}）の情報源を提供元・原本種別の順に並べる（S-16 の選択肢。Bolt 39c）。 */
    List<DataSource> findApproved();

    /** 承認済み（{@link DataSource.ApprovalStatus#APPROVED}）の情報源の件数。候補と停止中は数えない。 */
    int countApproved();
}
