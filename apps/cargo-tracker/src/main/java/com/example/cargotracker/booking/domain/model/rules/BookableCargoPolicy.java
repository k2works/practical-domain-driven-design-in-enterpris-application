package com.example.cargotracker.booking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;

/**
 * 本予約を確定できる貨物（B-INV-04、BR-03、US-04 AC5。Bolt 35）。見積りから写した貨物種別の名前が一般貨物のときだけ確定できる。
 * 見積りの提出の拒否（MvpAcceptancePolicy）に加えた二重の守りで、予約は見積りのドメインの型を使わず、写した名前だけで判定する
 * （BC の独立）。知らない値・空の値は確定しない側に倒す。
 */
@DomainRule
public final class BookableCargoPolicy {

    /** 一般貨物の名前（見積りの貨物種別 {@code GENERAL} の写し）。 */
    private static final String GENERAL = "GENERAL";

    private BookableCargoPolicy() {}

    /** その貨物種別の名前の見積りで本予約を確定できるか。 */
    public static boolean bookable(String cargoCategory) {
        return GENERAL.equals(cargoCategory);
    }
}
