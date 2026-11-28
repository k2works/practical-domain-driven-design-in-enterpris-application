package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 貨物種別（経路設計）。経路条件の貨物種別。見積りの貨物種別の名前を腐敗防止層で翻訳した、経路設計の型（D-65、Bolt 39i）。
 * 見積りの語彙を経路設計のドメインに直接入れない。保存は名前（`routing_case.cargo_category`）で、読むときは {@link #valueOf(String)}。
 */
@ValueObject
public enum RouteCargoCategory {
    /** 一般貨物。MVP の経路設計の対象。 */
    GENERAL,
    /** 危険物。 */
    DANGEROUS,
    /** 冷凍。 */
    REEFER,
    /** その他特殊。 */
    OTHER_SPECIAL,
    /** 知らない名前（見積りに値が増えたのに翻訳が追いついていない）。一般貨物として通さない（B-INV-04 の前例）。 */
    UNRECOGNIZED;

    /** 一般貨物か（MVP の経路設計の対象か）。 */
    public boolean general() {
        return this == GENERAL;
    }
}
