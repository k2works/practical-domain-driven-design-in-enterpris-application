package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import java.time.Instant;
import java.util.Objects;

/**
 * 内容の違う原本の判断の画面の値（S-17。Bolt 39d3 の開始準備の決定 (6)、【業務の判断】(i)・(j)）。判断を待つ隔離の照会の値。受け取った値と
 * その取得時刻、この外部版の採用値とその情報版・公開した時刻・取得時刻を並べる。39d3 は航海の値だけを持つ（主要実績の値と読めない値の印は
 * 39d3c で足す）。
 *
 * @param received 受け取った値（値の列を記録する前（Bolt 39d3 より前）に取り込んだ隔離は {@code null}）
 * @param receivedAcquiredAt 受け取った値の取得時刻（隔離の受信記録の取得時刻）
 * @param adopted この外部版の採用値（ないときは {@code null}）
 */
public record QuarantineValues(VoyageValues received, Instant receivedAcquiredAt, Adopted adopted) {

    public QuarantineValues {
        Objects.requireNonNull(receivedAcquiredAt, "receivedAcquiredAt");
    }

    /**
     * 受け取った値とこの外部版の採用値が等しいか（値の同一性。隔離はハッシュで決まるので、値の同じ原本も隔離になる）。どちらかがなければ偽。
     *
     * @return 等しいか
     */
    public boolean sameValues() {
        return received != null && adopted != null && received.equals(adopted.values());
    }

    /**
     * 受け取った値とこの外部版の採用値の取得時刻が同じか（取得時刻は原本のハッシュに入るが、値と要約に入らない）。採用値がなければ偽。
     *
     * @return 同じか
     */
    public boolean sameAcquiredAt() {
        return adopted != null && receivedAcquiredAt.equals(adopted.acquiredAt());
    }

    /**
     * この外部版の採用値。
     *
     * @param values 採用値の値（採用値の値の要約から戻した値）
     * @param infoVersion 情報版（外部版）
     * @param publishedAt 公開した時刻
     * @param acquiredAt 取得時刻（採用値の受信記録の取得時刻）
     */
    public record Adopted(VoyageValues values, String infoVersion, Instant publishedAt, Instant acquiredAt) {

        public Adopted {
            Objects.requireNonNull(values, "values");
            Objects.requireNonNull(infoVersion, "infoVersion");
            Objects.requireNonNull(publishedAt, "publishedAt");
            Objects.requireNonNull(acquiredAt, "acquiredAt");
        }
    }
}
