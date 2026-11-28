package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.UUID;

/**
 * 受信記録の判断のリポジトリ。判断を追記し、受信記録に判断があるかを答えるドメインのポート（Bolt 39d2）。判断の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため。前例 {@link ReceivedRecordRepository}）。受信記録より後に書く（外部キー）。
 */
public interface ReceivedRecordDecisionRepository {

    /**
     * 判断を追記する。同じ受信記録の 2 つ目の判断（同時の判断・二重の送信。一意制約の違反）は {@link DuplicateReceivedRecordDecisionException}。
     */
    void add(ReceivedRecordDecision decision);

    /** その受信記録に判断があるか。 */
    boolean existsFor(UUID receivedRecordId);
}
