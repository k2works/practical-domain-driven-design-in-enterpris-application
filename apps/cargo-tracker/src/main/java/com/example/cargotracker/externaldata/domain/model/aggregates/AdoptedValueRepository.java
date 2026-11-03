package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/**
 * 採用値のリポジトリ。採用値を追記し、受信記録 ID で引くドメインのポート（Bolt 39b3）。採用値の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため。前例は {@link IngestionRepository}）。採用値が受信記録への外部キーを持つので、呼び手は
 * 受信記録より後に書く。
 */
public interface AdoptedValueRepository {

    /** 採用値を追記する。同じ受信記録の 2 つ目は一意制約の違反になる（受信記録ごとに 1 つ）。 */
    void add(AdoptedValue adoptedValue);

    /** 受信記録の採用値を引く。ないときは空。 */
    Optional<AdoptedValue> findByReceivedRecordId(UUID receivedRecordId);
}
