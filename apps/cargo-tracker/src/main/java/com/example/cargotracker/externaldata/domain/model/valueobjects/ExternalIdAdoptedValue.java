package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * この外部 ID の採用値。一覧の 1 つで、同じ提供元・原本種別・外部 ID の、外部版を問わない採用値を差異の規則が比べるための値（Bolt 39h の開始準備の
 * 決定 (3)）。取得時刻は採用値でなく受信記録の列なので、採用値（{@code AdoptedValue}）に足さずにこの値で受け渡す。照会は並びを決めず、比べる
 * 相手は差異の規則が選ぶ。隔離の比べる相手の「この外部版の採用値」とは別。
 *
 * @param externalVersion 外部版（採用値の情報版）
 * @param targetKey 対象のキー（航海は航海番号、主要実績は追跡番号）
 * @param acquiredAt 取得時刻（採用値の受信記録の取得時刻。提供元の時計）
 * @param publishedAt 公開した時刻（取込での採用は取込の時刻、採用の判断は判断の時刻）
 * @param receivedRecordId 採用値の受信記録 ID
 */
@ValueObject
public record ExternalIdAdoptedValue(
        String externalVersion, String targetKey, Instant acquiredAt, Instant publishedAt, UUID receivedRecordId) {

    public ExternalIdAdoptedValue {
        Objects.requireNonNull(externalVersion, "externalVersion");
        Objects.requireNonNull(targetKey, "targetKey");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
    }
}
