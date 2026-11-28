package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 採用値。外部原本から採用し、業務に公開した値（data_model の {@code external_data.adopted_value}。Bolt 39b3）。分類が採用の受信記録と、採用と判断した隔離の
 * 受信記録（Bolt 39d3b）ごとに 1 つ、取込か判断と同じトランザクションで追記する（重複と判断を待つ隔離には作らない。ED-INV-02）。リポジトリのポートが受け渡す record で、振る舞いを
 * 持たない（トランザクションスクリプト。ADR-002）。追記専用で、書いた後に更新しない。出典は持たず、受信記録（取得時刻）と情報源（提供元）
 * から導く。
 *
 * @param id 採用値 ID
 * @param receivedRecordId 受信記録 ID
 * @param targetType 対象の種類
 * @param targetKey 対象のキー（航海なら航海番号、主要実績なら追跡番号。外部 ID とは限らない）
 * @param valueSummary 値の要約（取込の値から作る正規の文字列。{@value #VALUE_SUMMARY_MAX_LENGTH} コードポイントまで）
 * @param infoVersion 情報版（外部版）
 * @param publishedAt 公開した時刻（取込での採用は取込の時刻、採用の判断は判断の時刻）
 */
@Entity
public record AdoptedValue(
        UUID id,
        UUID receivedRecordId,
        TargetType targetType,
        String targetKey,
        String valueSummary,
        String infoVersion,
        Instant publishedAt) {

    /** 値の要約の上限の文字数（コードポイント。{@code value_summary VARCHAR(4000)}。受信記録の値と同じ上限。Bolt 39d3）。 */
    public static final int VALUE_SUMMARY_MAX_LENGTH = ReceivedValue.SUMMARY_MAX_LENGTH;

    public AdoptedValue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetKey, "targetKey");
        Objects.requireNonNull(valueSummary, "valueSummary");
        Objects.requireNonNull(infoVersion, "infoVersion");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (valueSummary.codePointCount(0, valueSummary.length()) > VALUE_SUMMARY_MAX_LENGTH) {
            throw new IllegalArgumentException("値の要約は " + VALUE_SUMMARY_MAX_LENGTH + " 文字まで: " + targetKey);
        }
    }

    /** 対象の種類（表の CHECK {@code ck_adopted_value_target_type} と同じ 2 値）。取込の値の型から決める。 */
    public enum TargetType {
        /** 航海。 */
        VOYAGE,
        /** 主要実績。 */
        MILESTONE
    }
}
