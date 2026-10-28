package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import java.util.Objects;
import java.util.Optional;

/**
 * 社内の側の輸送要求の引き当ての結果（Bolt 38f。US-16 AC4）。見つかった・ない・担当範囲の外のどれか。画面は、ない・範囲の外を共通の
 * 404 で見分けずに示し（範囲の外の存在を見せない）、範囲の外の確定の POST だけを試行として記録する。
 */
public sealed interface StaffTransportRequestLookup {

    /**
     * 担当範囲の中の輸送要求（見つかったときだけ）。照会（GET）は、ない・範囲の外を同じく空として 404 にする（記録しない。SEC-12）。
     * 2 つの社内の controller の引き当てを、この 1 か所にそろえる（Bolt 38f の開発レビュー M2）。
     */
    default Optional<TransportRequest> inScope() {
        return this instanceof Found(TransportRequest request) ? Optional.of(request) : Optional.empty();
    }

    /**
     * 操作者の担当企業の輸送要求。
     *
     * @param request 輸送要求
     */
    record Found(TransportRequest request) implements StaffTransportRequestLookup {

        public Found {
            Objects.requireNonNull(request, "request");
        }
    }

    /** 業務番号の輸送要求がない（記録しない）。 */
    record NotFound() implements StaffTransportRequestLookup {}

    /** 輸送要求はあるが、荷主企業が操作者の担当企業でない（担当企業がない操作者を含む）。 */
    record OutOfScope() implements StaffTransportRequestLookup {}
}
