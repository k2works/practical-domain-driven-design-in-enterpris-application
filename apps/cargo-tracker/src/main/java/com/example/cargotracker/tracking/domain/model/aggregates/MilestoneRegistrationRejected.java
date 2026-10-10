package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import java.util.Objects;

/**
 * 主要実績の登録を拒否した（Bolt 26b）。理由を値で持ち、画面が理由ごとの文言を示す（Bolt 26c）。
 */
public final class MilestoneRegistrationRejected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final MilestoneRejectionReason reason;

    public MilestoneRegistrationRejected(MilestoneRejectionReason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public MilestoneRejectionReason reason() {
        return reason;
    }
}
