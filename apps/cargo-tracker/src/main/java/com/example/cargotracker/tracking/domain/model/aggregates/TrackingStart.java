package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import java.util.Objects;

/**
 * 追跡の開始の結果。予定を採用した追跡記録と、発行する DE-22。
 *
 * @param trackingRecord 追跡記録
 * @param event 追跡を開始した（DE-22）
 */
public record TrackingStart(TrackingRecord trackingRecord, TrackingStarted event) {

    public TrackingStart {
        Objects.requireNonNull(trackingRecord, "trackingRecord");
        Objects.requireNonNull(event, "event");
    }
}
