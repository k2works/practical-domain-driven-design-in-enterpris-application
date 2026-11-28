package com.example.cargotracker.externaldata.application.internal.queryservices;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 1 つの取込の形式の誤りのまとめての却下の対象（S-17 のまとめての却下の画面の中身。Bolt 39d2b）。判断を待つ隔離の照会の値。
 *
 * @param ingestionId 取込 ID
 * @param ingestedAt 取り込んだ日時（取込の取込時刻）
 * @param provider 情報源の提供元
 * @param recordType 情報源の原本種別（取込ファイルの種別の名前）
 * @param count その取込の判断を待つキーのない隔離の件数
 */
public record KeylessRejectionTarget(
        UUID ingestionId, Instant ingestedAt, String provider, String recordType, int count) {

    public KeylessRejectionTarget {
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
    }
}
