package com.example.cargotracker.externaldata.application.internal.commandservices;

import java.util.Objects;
import java.util.UUID;

/**
 * 受信記録の判断の結果（{@link ReceivedRecordDecisionService}。Bolt 39d2）。断るときは判断を追記せず、イベントも出さない（例外にせず種類で返す）。
 * 判断できる受信記録は「取込で書いた受信記録で、分類が隔離で、判断がない」で、判断の画面（GET）と却下・採用（POST）で同じ定めを使う。
 */
public sealed interface DecisionOutcome {

    /**
     * 却下した。
     *
     * @param receivedRecordId 受信記録 ID
     */
    record Rejected(UUID receivedRecordId) implements DecisionOutcome {

        public Rejected {
            requireReceivedRecordId(receivedRecordId);
        }
    }

    /**
     * 採用した（Bolt 39d3b）。
     *
     * @param receivedRecordId 受信記録 ID
     */
    record AdoptedQuarantine(UUID receivedRecordId) implements DecisionOutcome {

        public AdoptedQuarantine {
            requireReceivedRecordId(receivedRecordId);
        }
    }

    /**
     * 判断を待つ隔離だが採用できない（Bolt 39d3b。却下はできる）。理由で画面の文を選び、受信記録 ID で判断の画面へ戻す（Bolt 39d3b2）。
     *
     * @param receivedRecordId 受信記録 ID
     * @param reason 採用できない理由
     */
    record NotAdoptable(UUID receivedRecordId, NotAdoptableReason reason) implements DecisionOutcome {

        public NotAdoptable {
            requireReceivedRecordId(receivedRecordId);
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 受信記録 ID を持つ結果の null の確かめ（3 つの結果で同じ）。 */
    private static void requireReceivedRecordId(UUID receivedRecordId) {
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
    }

    /** 採用できない理由（Bolt 39d3b2。業務の見分けで、画面の文は持たない）。判断のサービスがこの順に確かめる。 */
    enum NotAdoptableReason {
        /** 外部原本がない隔離（形式の誤り。【業務の判断】(a)）。 */
        KEYLESS,
        /** 受け取った値の列がない隔離（Bolt 39d の決定 (8)）。 */
        VALUE_NOT_RECORDED,
        /** 情報源の承認の状態が承認済みでない隔離（【業務の判断】(n)・(o)）。 */
        SOURCE_NOT_APPROVED,
        /** 外部原本の基準と同じハッシュの隔離（同じ原本がすでに採用されている。【業務の判断】(p)。却下で片付ける）。 */
        PAYLOAD_ALREADY_ADOPTED,
        /** 受け取った値を読めない隔離、知らない原本種別の隔離（【業務の判断】(k)）。 */
        VALUE_UNREADABLE,
        /** 受け取った値の追跡番号がこの外部版の採用値と違う主要実績の隔離（【業務の判断】(m)）。 */
        TRACKING_NUMBER_DIFFERS
    }

    /** 操作者がデータ責任者でない（URL の認可に加えてサービスでも確かめる）。 */
    record NotPermitted() implements DecisionOutcome {}

    /** 受信記録がない、または取込で書いた受信記録でない。 */
    record NotFound() implements DecisionOutcome {}

    /** 受信記録が隔離でない（採用・重複。差異は 39h で判断できるようにする）。 */
    record NotDecidable() implements DecisionOutcome {}

    /** 判断済み（2 人のデータ責任者・二重の送信。同時の判断の一意制約の違反を含む）。 */
    record AlreadyDecided() implements DecisionOutcome {}
}
