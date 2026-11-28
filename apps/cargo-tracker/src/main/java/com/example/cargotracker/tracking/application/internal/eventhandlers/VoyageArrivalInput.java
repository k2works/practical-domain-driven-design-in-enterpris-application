package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;

/**
 * DE-12 の入力の検査と追跡の言葉への変換（Bolt 40-1 の M3。W7 週全体レビュー）。イベントの内容の欠けは腐敗防止の責務で、listener の都合なので、値オブジェクトでなく
 * ここに置く。欠けていれば {@link InvalidEventValueException}（再配信しても直らないので、listener は ERROR のログを残して捨てる）。出典の参照の空と取得時刻の null は
 * 共有カーネルの {@code Source} が拒むので、ここでは見ない。
 */
final class VoyageArrivalInput {

    /** 航海の寄港の下限の数（経路設計の航海・外部データの航海の値と同じ）。 */
    static final int MIN_PORT_CALLS = 2;

    private VoyageArrivalInput() {}

    /** 届いた航海番号（空なら内容の欠け）。 */
    static String voyageNumberOf(String voyageNumber) {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new InvalidEventValueException("voyageNumber");
        }
        return voyageNumber;
    }

    /** 届いた寄港の並び（null か下限より少なければ内容の欠け）。 */
    static List<VoyagePortCall> portCallsOf(List<VoyagePortCall> portCalls) {
        if (portCalls == null || portCalls.size() < MIN_PORT_CALLS) {
            throw new InvalidEventValueException("portCalls");
        }
        return List.copyOf(portCalls);
    }

    /** 届いた寄港の値から作る（港が欠けていれば内容の欠け）。 */
    static VoyagePortCall portCallOf(Location port, UtcInstant arrivalAt, UtcInstant departureAt) {
        if (port == null) {
            throw new InvalidEventValueException("portCalls.port");
        }
        return VoyagePortCall.of(port, arrivalAt, departureAt);
    }

    /** 届いた公開した時刻と出典から公開の順を作る（欠けていれば内容の欠け。参照は受信記録 ID）。 */
    static PublicationOrder orderOf(UtcInstant publishedAt, Source source) {
        if (publishedAt == null) {
            throw new InvalidEventValueException("publishedAt");
        }
        if (source == null) {
            throw new InvalidEventValueException("source");
        }
        return new PublicationOrder(publishedAt, source.acquiredAt(), source.reference());
    }

    /** 届いた DE-12 の値が欠けている（内容の欠けたイベント。listener はこの型だけを捨てる）。 */
    static final class InvalidEventValueException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        /** @param detail 欠けている値の名前（ログに出す） */
        InvalidEventValueException(String detail) {
            super("航海の採用値の内容が欠けている: " + detail);
        }
    }
}
