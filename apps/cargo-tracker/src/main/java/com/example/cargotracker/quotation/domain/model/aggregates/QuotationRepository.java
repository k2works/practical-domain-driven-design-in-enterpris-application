package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.List;
import java.util.Optional;

/**
 * 見積りのリポジトリ（送信ポート）。見積りは輸送要求と別の集約で、輸送要求 ID と見積り番号で探す。
 * 荷主企業での絞り込みは、輸送要求を荷主企業で絞って照会してから行う（Q-INV-08）。
 */
public interface QuotationRepository {

    /**
     * 新しい見積りを保存する。同じ輸送要求の同じ見積り番号は保存できない（UK）。
     *
     * @throws DuplicateQuotationException 同じ見積り番号の見積りが先に保存されていた（同時の算出）
     */
    void save(Quotation quotation);

    /**
     * 見積りを更新する。読み込んだときの集約の版で照合する。
     *
     * @throws ConcurrentQuotationUpdateException 読み込んだ後に、ほかの更新が先に保存されていた
     */
    void update(Quotation quotation);

    /** 輸送要求の見積りを、見積り番号の順に返す。 */
    List<Quotation> findByTransportRequestId(TransportRequestId transportRequestId);

    /**
     * 見積提示済みの見積依頼ごとに、最新の見積り（見積り番号の最大）を、有効期限の近い順に返す（受付一覧 S-02。Bolt 11 レビュー R-02）。
     * 社内の照会で、荷主企業で絞らない。
     */
    List<QuotedRequestSummary> findLatestOfQuotedRequests();

    /**
     * 経路設計中の見積依頼ごとに、詳細設計依頼済みの見積りを、依頼時刻の古い順に返す（受付一覧 S-02。US-24 AC1。Bolt 12）。
     * 社内の照会で、荷主企業で絞らない。
     */
    List<RoutingRequestedSummary> findRoutingRequestedSummaries();

    /** 見積りを ID で探す（経路の割当て。経路設計は依頼元の見積り ID を持つ。Bolt 20）。 */
    Optional<Quotation> findById(QuotationId id);

    /** 輸送要求の見積りを見積り番号で探す。 */
    Optional<Quotation> findByTransportRequestIdAndNo(TransportRequestId transportRequestId, int quotationNo);
}
