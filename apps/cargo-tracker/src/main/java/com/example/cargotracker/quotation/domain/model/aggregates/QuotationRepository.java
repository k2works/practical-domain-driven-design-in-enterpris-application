package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.List;
import java.util.Optional;

/**
 * 見積りのリポジトリ（送信ポート）。見積りは輸送要求と別の集約で、輸送要求 ID と見積り番号で探す。
 * 荷主企業での絞り込みは、輸送要求を荷主企業で絞って照会してから行う（Q-INV-08）。
 */
public interface QuotationRepository {

    /** 新しい見積りを保存する。同じ輸送要求の同じ見積り番号は保存できない（UK）。 */
    void save(Quotation quotation);

    /**
     * 見積りを更新する。読み込んだときの集約の版で照合する。
     *
     * @throws ConcurrentQuotationUpdateException 読み込んだ後に、ほかの更新が先に保存されていた
     */
    void update(Quotation quotation);

    /** 輸送要求の見積りを、見積り番号の順に返す。 */
    List<Quotation> findByTransportRequestId(TransportRequestId transportRequestId);

    /** 輸送要求の見積りを見積り番号で探す。 */
    Optional<Quotation> findByTransportRequestIdAndNo(TransportRequestId transportRequestId, int quotationNo);
}
