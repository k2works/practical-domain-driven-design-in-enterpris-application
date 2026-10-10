package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import java.util.Objects;

/**
 * 主要実績の登録の結果。実績を足して現在状態を導出し直した追跡記録と、登録した実績。同じ出典の実績が登録済みなら、
 * 追跡記録は元のままで、実績は既存の実績（T-INV-02）。
 *
 * @param trackingRecord 追跡記録
 * @param milestone 登録した実績、または既存の実績
 * @param alreadyRegistered 同じ出典の実績が登録済みだったか
 */
public record MilestoneRegistration(TrackingRecord trackingRecord, Milestone milestone, boolean alreadyRegistered) {

    public MilestoneRegistration {
        Objects.requireNonNull(trackingRecord, "trackingRecord");
        Objects.requireNonNull(milestone, "milestone");
    }
}
