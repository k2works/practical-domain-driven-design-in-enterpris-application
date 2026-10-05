package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 見積有効期限。UTC の時点で持ち、画面では利用者のタイムゾーンと UTC offset を示す（BR-10）。
 * 予約確定の commit 時刻がこれより前のときだけ見積りは有効（判定は Bolt 11 で足す）。
 *
 * @param expiresAt 期限
 */
@ValueObject
public record QuotationExpiry(UtcInstant expiresAt) {

    public QuotationExpiry {
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
