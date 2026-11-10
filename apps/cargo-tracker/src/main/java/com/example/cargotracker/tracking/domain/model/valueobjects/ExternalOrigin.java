package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 実績の外部の識別。DE-13 から受けた実績の情報源と外部 ID と公開した時刻（Bolt 39g の決定 (2)・(3)）。同じ主要実績の組（追跡番号・情報源 ID・外部 ID）と、
 * 順の規則（公開した時刻 → 取得時刻 → 受信記録 ID）に使う（判定は 39gb）。外部版と情報版は持たない（順に使わず、受信記録 ID から外部データの原本にたどれる）。
 *
 * @param dataSourceId 情報源 ID
 * @param externalId 外部 ID（提供元の中でだけ一意）
 * @param publishedAt 外部データが採用値を公開した時刻
 */
@ValueObject
public record ExternalOrigin(UUID dataSourceId, String externalId, UtcInstant publishedAt) {

    public ExternalOrigin {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("外部 ID が空です");
        }
    }
}
