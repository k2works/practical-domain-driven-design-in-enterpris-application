package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.UUID;

/**
 * 受信記録のリポジトリ。受信記録を追記し、取込と分類ごとに数えるドメインのポート（Bolt 39b）。受信記録の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため）。呼び手は原本の保存に成功してから書く。
 */
public interface ReceivedRecordRepository {

    /** 受信記録を追記する。 */
    void add(ReceivedRecord receivedRecord);

    /** その取込の、その分類の受信記録の件数。 */
    int countByIngestionAndClassification(UUID ingestionId, Classification classification);
}
