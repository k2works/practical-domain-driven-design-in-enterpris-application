package com.example.cargotracker.externaldata.domain.model.aggregates;

/**
 * 取込のリポジトリ。取込を追記するドメインのポート（Bolt 39b）。取込の表は追記専用なので、更新・削除のメソッドを持たない（H2 は権限で
 * 守れないため。前例は {@code identity.domain.model.aggregates.AuditRecordRepository}）。受信記録が取込への外部キーを持つので、呼び手は
 * 受信記録より先に書く。
 */
public interface IngestionRepository {

    /** 取込を追記する。 */
    void add(Ingestion ingestion);
}
