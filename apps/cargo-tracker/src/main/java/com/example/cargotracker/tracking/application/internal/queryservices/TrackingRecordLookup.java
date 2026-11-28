package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import java.util.Objects;
import java.util.Optional;

/**
 * 社内の側の追跡記録の引き当ての結果（Bolt 38g。US-16 AC4）。見つかった・ない・担当範囲の外のどれか。画面は、ない・範囲の外を共通の
 * 404 で見分けずに示し（範囲の外の存在を見せない）、範囲の外のマイルストーンの登録（POST）だけを試行として記録する。38f の
 * {@code StaffTransportRequestLookup} の写し。
 */
public sealed interface TrackingRecordLookup {

    /** 担当範囲の中の追跡記録（見つかったときだけ）。照会（GET）は、ない・範囲の外を同じく空として 404 にする（記録しない。SEC-12）。 */
    default Optional<TrackingRecord> inScope() {
        return this instanceof Found(TrackingRecord trackingRecord) ? Optional.of(trackingRecord) : Optional.empty();
    }

    /**
     * 操作者の担当企業の追跡記録。
     *
     * @param trackingRecord 追跡記録
     */
    record Found(TrackingRecord trackingRecord) implements TrackingRecordLookup {

        public Found {
            Objects.requireNonNull(trackingRecord, "trackingRecord");
        }
    }

    /** 追跡番号の追跡記録がない（記録しない）。 */
    record NotFound() implements TrackingRecordLookup {}

    /** 追跡記録はあるが、荷主企業が操作者の担当企業でない（担当企業がない操作者を含む）。 */
    record OutOfScope() implements TrackingRecordLookup {}
}
