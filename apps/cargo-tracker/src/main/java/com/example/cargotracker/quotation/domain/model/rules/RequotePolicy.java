package com.example.cargotracker.quotation.domain.model.rules;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 見積りを再見積りできるか（Q-INV-07・18、D-85。Bolt 35c）。S-04 の再見積りのボタン・GET と再見積りのコマンド（POST）が同じ順で
 * 同じ理由を示すよう、判定をこの規則にまとめる（S-09 の {@code BookableCargoPolicy} と同じ形）。順は、輸送要求の状態（予約確定済み →
 * 再見積りできる状態でない）→ 輸送要求の版 → 見積りの判定時刻の判定（{@link Quotation#requoteRejectionAt}）。見つからないかは
 * 呼び手が先に見る。2 つの集約を読むだけで、変えない。
 */
@DomainRule
public final class RequotePolicy {

    /**
     * 再見積りできる見積依頼の状態（見積提示済みは DE-03 を受けた後）。荷主の承認の後に失効した見積りの再見積り（D-85。Bolt 35b）の
     * ため、経路設計中・荷主承認待ち・予約待ちも含める（経路設計中・荷主承認待ちは DE-21・DE-04 の配信の遅れのとき）。経路設計の途中の
     * 見積りは見積りの判定が拒む。予約確定済みは含めない（理由を分ける。Bolt 35c）。
     */
    private static final Set<TransportRequestStatus> REQUOTABLE = EnumSet.of(
            TransportRequestStatus.QUOTING,
            TransportRequestStatus.QUOTED,
            TransportRequestStatus.ROUTING,
            TransportRequestStatus.AWAITING_APPROVAL,
            TransportRequestStatus.READY_TO_BOOK);

    private RequotePolicy() {}

    /**
     * 見積りを再見積りできないなら、その理由。
     *
     * @param request 見積りの輸送要求
     * @param quotation 再見積りの旧版
     * @param at 判定時刻
     * @return 再見積りできない理由（再見積りできるなら空）
     */
    public static Optional<QuotationRejection> rejection(TransportRequest request, Quotation quotation, UtcInstant at) {
        if (request.status() == TransportRequestStatus.BOOKED) {
            return Optional.of(QuotationRejection.TRANSPORT_REQUEST_BOOKED);
        }
        if (!REQUOTABLE.contains(request.status())) {
            return Optional.of(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING);
        }
        if (quotation.transportRequestVersionNo() != request.currentVersion().versionNo()) {
            return Optional.of(QuotationRejection.OUTDATED_VERSION);
        }
        return quotation.requoteRejectionAt(at);
    }
}
