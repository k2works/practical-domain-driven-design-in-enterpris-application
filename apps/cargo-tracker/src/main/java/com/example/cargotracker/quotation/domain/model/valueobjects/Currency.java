package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの通貨（ISO 4217）。見積りに 1 つで、料金明細はすべて同じ通貨（2026-10-05 の決定。候補は 2026-10-06 に決定。D-36-1）。
 */
public enum Currency {
    /** 米ドル。 */
    USD,
    /** ユーロ。 */
    EUR,
    /** 日本円。 */
    JPY
}
