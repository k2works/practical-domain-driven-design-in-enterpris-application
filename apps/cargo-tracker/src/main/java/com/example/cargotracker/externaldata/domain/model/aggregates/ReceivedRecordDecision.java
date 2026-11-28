package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DecisionKind;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * 受信記録の判断。隔離と差異の受信記録に対するデータ責任者の判断の追記専用の記録で、受信記録 1 件につき 1 つ（data_model の
 * {@code external_data.received_record_decision}。US-14 AC4。Bolt 39d2）。リポジトリのポートが受け渡す record で、集約を持たない
 * （ADR-002。前例 {@link ReceivedRecord}）。規則はコンストラクタの不変条件として持つ: 却下は理由を必須にし（前後の空白を除いて 1 文字以上）、
 * 理由は {@link #REASON_LIMIT} 文字（コードポイント）まで（【業務の判断】(d)）。判断するのは隔離と差異の受信記録だけで、採用は隔離だけ（差異は却下だけ。Bolt 39h2）。却下と採用のファクトリーが
 * 守る（表の制約は足さない。data_model の受信記録の判断）。判断は取り消さない（【業務の判断】(e)）。
 *
 * @param id 受信記録の判断 ID
 * @param receivedRecordId 受信記録 ID
 * @param decision 判断の種類
 * @param decidedBy 判断者
 * @param decidedAt 判断の時刻
 * @param reason 理由（却下は必須。採用は任意で、ないときは {@code null}。Bolt 39d3b）
 */
@Entity
public record ReceivedRecordDecision(
        UUID id, UUID receivedRecordId, DecisionKind decision, UserId decidedBy, Instant decidedAt, String reason) {

    /** 理由の上限（コードポイント。{@code reason VARCHAR(1000)}）。 */
    public static final int REASON_LIMIT = 1000;

    public ReceivedRecordDecision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(decidedBy, "decidedBy");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (decision == DecisionKind.REJECTED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("却下の理由は必須: " + receivedRecordId);
        }
        if (reason != null && reason.codePointCount(0, reason.length()) > REASON_LIMIT) {
            throw new IllegalArgumentException("理由は " + REASON_LIMIT + " 文字まで: " + receivedRecordId);
        }
    }

    /** 判断できる受信記録か（分類が隔離か差異。Bolt 39h2）。却下のファクトリーと判断のサービスが同じ規則を使う。 */
    public static boolean decidable(ReceivedRecord receivedRecord) {
        return receivedRecord.classification() == Classification.QUARANTINED
                || receivedRecord.classification() == Classification.DISCREPANCY;
    }

    /**
     * 隔離か差異の受信記録を却下する。理由は前後の空白を除いて持ち、判断の時刻はマイクロ秒に切り捨てる（DB の精度）。
     *
     * @throws IllegalArgumentException 受信記録が隔離でも差異でもない、または理由が空・上限を超える
     */
    public static ReceivedRecordDecision reject(
            ReceivedRecord receivedRecord, String reason, UserId decidedBy, Instant decidedAt) {
        return decide(
                receivedRecord, DecisionKind.REJECTED, reason == null ? null : reason.strip(), decidedBy, decidedAt);
    }

    /**
     * 隔離の受信記録を採用する（Bolt 39d3b）。理由は任意で、空白だけの理由は持たず（{@code null}）、ほかは前後の空白を除いて持つ。判断の時刻は
     * マイクロ秒に切り捨てる（DB の精度）。
     *
     * @throws IllegalArgumentException 受信記録が隔離でない（差異を含む）、または理由が上限を超える
     */
    public static ReceivedRecordDecision adopt(
            ReceivedRecord receivedRecord, String reason, UserId decidedBy, Instant decidedAt) {
        Objects.requireNonNull(receivedRecord, "receivedRecord");
        if (receivedRecord.classification() != Classification.QUARANTINED) {
            // 差異は採用しない（却下だけ。判断のサービスの定めを通らずに呼ばれても採用の判断を作らない。Bolt 39h2 の決定 (3)）
            throw new IllegalArgumentException("採用するのは隔離の受信記録だけ: " + receivedRecord.classification());
        }
        String stripped = reason == null ? null : reason.strip();
        return decide(
                receivedRecord,
                DecisionKind.ADOPTED,
                stripped == null || stripped.isEmpty() ? null : stripped,
                decidedBy,
                decidedAt);
    }

    private static ReceivedRecordDecision decide(
            ReceivedRecord receivedRecord, DecisionKind decision, String reason, UserId decidedBy, Instant decidedAt) {
        Objects.requireNonNull(receivedRecord, "receivedRecord");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (!decidable(receivedRecord)) {
            throw new IllegalArgumentException("判断するのは隔離か差異の受信記録だけ: " + receivedRecord.classification());
        }
        return new ReceivedRecordDecision(
                UUID.randomUUID(),
                receivedRecord.id(),
                decision,
                decidedBy,
                decidedAt.truncatedTo(ChronoUnit.MICROS),
                reason);
    }
}
