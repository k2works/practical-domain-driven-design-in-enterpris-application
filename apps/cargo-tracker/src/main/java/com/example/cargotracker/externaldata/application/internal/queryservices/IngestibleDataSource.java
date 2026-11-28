package com.example.cargotracker.externaldata.application.internal.queryservices;

import java.util.Objects;
import java.util.UUID;

/**
 * 取り込める情報源。S-16 の選択肢 1 つ（取り込める情報源の照会の値。Bolt 39c）。
 *
 * @param id 情報源 ID
 * @param provider 提供元
 * @param recordType 原本種別（{@code data_source.record_type}。取込ファイルの種別の名前）
 */
public record IngestibleDataSource(UUID id, String provider, String recordType) {

    public IngestibleDataSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(recordType, "recordType");
    }
}
