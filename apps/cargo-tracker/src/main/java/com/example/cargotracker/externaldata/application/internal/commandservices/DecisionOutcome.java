package com.example.cargotracker.externaldata.application.internal.commandservices;

import java.util.Objects;
import java.util.UUID;

/**
 * 受信記録の判断の結果（{@link ReceivedRecordDecisionService}。Bolt 39d2）。断るときは判断を追記せず、イベントも出さない（例外にせず種類で返す）。
 * 判断できる受信記録は「取込で書いた受信記録で、分類が隔離で、判断がない」で、判断の画面（GET）と却下（POST）で同じ定めを使う。
 */
public sealed interface DecisionOutcome {

    /**
     * 却下した。
     *
     * @param receivedRecordId 受信記録 ID
     */
    record Rejected(UUID receivedRecordId) implements DecisionOutcome {

        public Rejected {
            Objects.requireNonNull(receivedRecordId, "receivedRecordId");
        }
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
