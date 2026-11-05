package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/**
 * 採用値のリポジトリ。採用値を追記し、受信記録 ID で引き、外部原本のこの外部版の採用値を引くドメインのポート（Bolt 39b3。この外部版の
 * 採用値は Bolt 39d3）。採用値の表は追記専用なので、更新・削除の
 * メソッドを持たない（H2 は権限で守れないため。前例は {@link IngestionRepository}）。採用値が受信記録への外部キーを持つので、呼び手は
 * 受信記録より後に書く。
 */
public interface AdoptedValueRepository {

    /**
     * 採用値を追記する。受信記録ごとに 1 つ。
     *
     * @param adoptedValue 採用値
     * @throws DuplicateAdoptedValueException 同じ受信記録の採用値がもうあるとき（一意制約の違反）
     */
    void add(AdoptedValue adoptedValue);

    /** 受信記録の採用値を引く。ないときは空。 */
    Optional<AdoptedValue> findByReceivedRecordId(UUID receivedRecordId);

    /**
     * この外部版の採用値（Bolt 39d3 の開始準備の決定 (5)、【業務の判断】(j)）。同じ外部原本（同じ提供元・原本種別・外部 ID・外部版）の受信記録に
     * 付いた採用値のうち、公開した時刻の最も新しいもの（同じ時刻なら採用値 ID の順の最後）。同じ外部 ID の別の外部版の採用値は含めない
     * （外部版が違えば別の外部原本）。ないときは空。
     *
     * @param externalRecordId 外部原本 ID
     */
    Optional<AdoptedValue> currentOf(UUID externalRecordId);
}
