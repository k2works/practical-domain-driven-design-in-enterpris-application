package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import java.util.Objects;

/**
 * 経路の確定を拒否した（US-07 AC2。Bolt 19）。理由を値で持ち、画面が理由ごとの文言を示す。
 */
public final class RouteConfirmationRejected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final RouteConfirmationRejectionReason reason;

    public RouteConfirmationRejected(RouteConfirmationRejectionReason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public RouteConfirmationRejectionReason reason() {
        return reason;
    }
}
