package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 区間。1 つの航海で積地から揚地まで運ぶ部分。出発予定・到着予定は航海の寄港から取る。
 * 判定の根拠を示すため、区間を取った航海の採用情報版と取得時刻を写して持つ（R-INV-02。Bolt 17）。
 *
 * @param voyageNumber 航海番号
 * @param load 積地
 * @param discharge 揚地
 * @param departureAt 出発予定（積地）
 * @param arrivalAt 到着予定（揚地）
 * @param infoVersion 航海の採用情報版
 * @param infoAcquiredAt 航海の情報の取得時刻
 * @param loadCallNo 積地の寄港の位置（航海の運航の順で 1 始まり。Bolt 39f より前に算出した区間は null）
 * @param dischargeCallNo 揚地の寄港の位置
 */
@ValueObject
public record Leg(
        String voyageNumber,
        Location load,
        Location discharge,
        UtcInstant departureAt,
        UtcInstant arrivalAt,
        String infoVersion,
        UtcInstant infoAcquiredAt,
        Integer loadCallNo,
        Integer dischargeCallNo) {

    /** 寄港の位置を持たない区間（Bolt 39f より前に算出した区間と、位置が要らないテスト）。 */
    public Leg(
            String voyageNumber,
            Location load,
            Location discharge,
            UtcInstant departureAt,
            UtcInstant arrivalAt,
            String infoVersion,
            UtcInstant infoAcquiredAt) {
        this(voyageNumber, load, discharge, departureAt, arrivalAt, infoVersion, infoAcquiredAt, null, null);
    }

    public Leg {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(discharge, "discharge");
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        Objects.requireNonNull(infoVersion, "infoVersion");
        Objects.requireNonNull(infoAcquiredAt, "infoAcquiredAt");
        if (!arrivalAt.instant().isAfter(departureAt.instant())) {
            throw new IllegalArgumentException("到着予定が出発予定より後ではありません: " + voyageNumber);
        }
        if ((loadCallNo == null) != (dischargeCallNo == null)) {
            throw new IllegalArgumentException("寄港の位置は積地と揚地をそろえて持ちます: " + voyageNumber);
        }
        if (loadCallNo != null && loadCallNo >= dischargeCallNo) {
            throw new IllegalArgumentException("積地の寄港の位置は揚地より前です: " + voyageNumber);
        }
    }

    /** 寄港の位置を持つか（Bolt 39f より前に算出した区間は持たない）。 */
    public boolean hasCallPositions() {
        return loadCallNo != null;
    }
}
