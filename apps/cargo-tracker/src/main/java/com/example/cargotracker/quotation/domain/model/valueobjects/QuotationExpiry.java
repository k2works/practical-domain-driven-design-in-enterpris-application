package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 見積有効期限。UTC の時点で持ち、画面では利用者のタイムゾーンと UTC offset を示す（BR-10）。
 * 判定時刻がこれより前のときだけ見積りは有効で、同時刻からは失効（BR-10、Q-INV-06）。
 *
 * @param expiresAt 期限
 */
@ValueObject
public record QuotationExpiry(UtcInstant expiresAt) {

    public QuotationExpiry {
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * 判定時刻に有効か。判定時刻が期限より前なら有効、同時刻または後なら失効（同時刻は失効。BR-10）。
     *
     * @param judgedAt 判定時刻（呼び出し側が渡す。提示・再見積りは操作の時刻、予約確定は commit 時刻）
     */
    public boolean isValidAt(UtcInstant judgedAt) {
        return judgedAt.instant().isBefore(expiresAt.instant());
    }
}
