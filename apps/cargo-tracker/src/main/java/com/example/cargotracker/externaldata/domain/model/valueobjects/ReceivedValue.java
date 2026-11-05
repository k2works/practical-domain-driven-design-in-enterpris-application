package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 受信記録の値。外部原本を持つ受信記録（採用・重複・内容の違う原本の隔離）に取込で残す、取込の値の対象のキーと値の要約（data_model の
 * {@code external_data.received_record} の {@code target_key}・{@code value_summary}。Bolt 39d3 の開始準備の決定 (3)）。採用値と同じ規則の
 * 文字列で、航海も主要実績も型で分ける入口 {@link RecordValues#ofSummary} で値に戻せる（Bolt 39d3c）。キーに独自の上限は置かない（取込の
 * 値の型が守る。航海番号はコードポイントで 30 文字、追跡番号は 14 文字。表の {@code VARCHAR(100)} はその外側の守り）。
 *
 * @param targetKey 対象のキー（航海は航海番号、主要実績は追跡番号）
 * @param summary 値の要約（{@value #SUMMARY_MAX_LENGTH} コードポイントまで。採用値の値の要約と同じ上限）
 */
@ValueObject
public record ReceivedValue(String targetKey, String summary) {

    /** 値の要約の上限の文字数（コードポイント。{@code value_summary VARCHAR(4000)}。採用値の値の要約の上限もこの値を使う）。 */
    public static final int SUMMARY_MAX_LENGTH = 4000;

    public ReceivedValue {
        Objects.requireNonNull(targetKey, "targetKey");
        Objects.requireNonNull(summary, "summary");
        if (summary.codePointCount(0, summary.length()) > SUMMARY_MAX_LENGTH) {
            throw new IllegalArgumentException("値の要約は " + SUMMARY_MAX_LENGTH + " 文字まで: " + targetKey);
        }
    }

    /**
     * 取込の値から作る（キーは {@link RecordValues#targetKey()}、要約は {@link RecordValues#summary()}）。
     *
     * @param values 取込の値
     * @return 受信記録の値
     */
    public static ReceivedValue of(RecordValues values) {
        return new ReceivedValue(values.targetKey(), values.summary());
    }
}
