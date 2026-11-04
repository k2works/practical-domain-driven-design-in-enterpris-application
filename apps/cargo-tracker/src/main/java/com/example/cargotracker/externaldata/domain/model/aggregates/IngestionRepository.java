package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/**
 * 取込のリポジトリ。取込を追記し ID で引くドメインのポート（Bolt 39b。引くのは Bolt 39c）。取込の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため。前例は {@code identity.domain.model.aggregates.AuditRecordRepository}）。受信記録が
 * 取込への外部キーを持つので、呼び手は受信記録より先に書く。
 */
public interface IngestionRepository {

    /** 取込を追記する。 */
    void add(Ingestion ingestion);

    /** ID で取込を引く（S-16 の結果の画面。Bolt 39c）。ないときは空。 */
    Optional<Ingestion> findById(UUID id);
}
