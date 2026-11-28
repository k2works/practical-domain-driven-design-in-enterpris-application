package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import java.util.Objects;
import java.util.Optional;

/**
 * 社内の側の経路設計案件の引き当ての結果（Bolt 38g。US-16 AC4）。見つかった・ない・担当範囲の外のどれか。画面は、ない・範囲の外を共通の
 * 404 で見分けずに示し（範囲の外の存在を見せない）、範囲の外の状態を変える POST だけを試行として記録する。38f の
 * {@code StaffTransportRequestLookup} の写し。
 */
public sealed interface RoutingCaseLookup {

    /** 担当範囲の中の案件（見つかったときだけ）。照会（GET）は、ない・範囲の外を同じく空として 404 にする（記録しない。SEC-12）。 */
    default Optional<RoutingCase> inScope() {
        return this instanceof Found(RoutingCase routingCase) ? Optional.of(routingCase) : Optional.empty();
    }

    /**
     * 操作者の担当企業の案件。
     *
     * @param routingCase 経路設計案件
     */
    record Found(RoutingCase routingCase) implements RoutingCaseLookup {

        public Found {
            Objects.requireNonNull(routingCase, "routingCase");
        }
    }

    /** 案件番号の案件がない（記録しない）。 */
    record NotFound() implements RoutingCaseLookup {}

    /** 案件はあるが、荷主企業が操作者の担当企業でない（担当企業がない操作者を含む）。 */
    record OutOfScope() implements RoutingCaseLookup {}
}
