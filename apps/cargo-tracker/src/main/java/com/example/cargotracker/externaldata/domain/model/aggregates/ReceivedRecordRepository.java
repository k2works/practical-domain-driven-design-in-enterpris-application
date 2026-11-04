package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.List;
import java.util.UUID;

/**
 * 受信記録のリポジトリ。受信記録を追記し、取込と分類ごとに数え、取込の隔離を行の範囲の順に読むドメインのポート（Bolt 39b。読む照会は
 * Bolt 39c3）。受信記録の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため）。呼び手は原本の保存に成功してから書く。
 */
public interface ReceivedRecordRepository {

    /** 受信記録を追記する。 */
    void add(ReceivedRecord receivedRecord);

    /** その取込の、その分類の受信記録の件数。 */
    int countByIngestionAndClassification(UUID ingestionId, Classification classification);

    /**
     * その取込の隔離の受信記録を、行の範囲の始まりの順（範囲のないファイルの誤りを先、同じなら受信記録 ID の順）に最大 {@code limit} 件
     * （S-16 の結果の画面の隔離の理由。Bolt 39c3）。
     */
    List<ReceivedRecord> quarantinedOf(UUID ingestionId, int limit);
}
