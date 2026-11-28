package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhaseRefusal;
import java.util.Objects;

/**
 * 集荷・引渡しの記録の結果（{@link Booking#recordPickup()}・{@link Booking#recordDelivery()}。Bolt 39g2）。進めた予約は呼び手が保存する。記録済みと
 * 記録しないは保存しない（版を上げない）。
 */
public sealed interface TransportPhaseChange {

    /**
     * 予約の状態と輸送段階を進めた。
     *
     * @param booking 進めた予約（版はリポジトリが保存のときに増やす）
     */
    record Changed(Booking booking) implements TransportPhaseChange {

        public Changed {
            Objects.requireNonNull(booking, "booking");
        }
    }

    /** すでに記録済み（同じ通知の再実行か、後から届いた古い通知。段を戻さない）。 */
    record Unchanged() implements TransportPhaseChange {}

    /**
     * 記録しない。
     *
     * @param reason 理由
     */
    record Refused(TransportPhaseRefusal reason) implements TransportPhaseChange {

        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
