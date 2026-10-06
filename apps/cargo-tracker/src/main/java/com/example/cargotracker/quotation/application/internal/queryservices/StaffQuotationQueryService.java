package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 社内の営業担当者が見積りを照会する入力ポート（S-04）。営業担当者はすべての荷主の見積依頼を扱うため、荷主企業で絞らない。
 * 荷主の画面から誤って使わないよう、荷主用の {@link QuotationQueryService} と分ける（ArchUnit の規則が守る）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class StaffQuotationQueryService {

    private final TransportRequestRepository transportRequestRepository;
    private final QuotationRepository quotationRepository;

    public StaffQuotationQueryService(
            TransportRequestRepository transportRequestRepository, QuotationRepository quotationRepository) {
        this.transportRequestRepository = transportRequestRepository;
        this.quotationRepository = quotationRepository;
    }

    /** 見積依頼の、作成中・承認待ち・提示済みの見積り（1 つだけ。Q-INV-18）。算出した後に画面を離れても戻れるようにする（R-04）。 */
    @Transactional(readOnly = true)
    public Optional<Quotation> findActive(TransportRequestNumber number) {
        return transportRequestRepository
                .findByNumberForStaff(number)
                .flatMap(request -> quotationRepository.findByTransportRequestId(request.id()).stream()
                        .filter(Quotation::isActive)
                        .max(Comparator.comparingInt(Quotation::quotationNo)));
    }

    /** 業務番号と見積り番号で見積りを照会する。 */
    @Transactional(readOnly = true)
    public Optional<Quotation> find(TransportRequestNumber number, int quotationNo) {
        return transportRequestRepository
                .findByNumberForStaff(number)
                .flatMap(request -> quotationRepository.findByTransportRequestIdAndNo(request.id(), quotationNo));
    }

    /** 見積依頼の見積りを、見積り番号の順にすべて返す（置換先の見積り番号を示すため。Bolt 11）。見積依頼がなければ空。 */
    @Transactional(readOnly = true)
    public List<Quotation> findAll(TransportRequestNumber number) {
        return transportRequestRepository
                .findByNumberForStaff(number)
                .map(request -> quotationRepository.findByTransportRequestId(request.id()))
                .orElse(List.of());
    }

    /** 受付一覧（S-02）の見積提示済みの表。見積依頼ごとの最新の見積りを、有効期限の近い順に返す（Bolt 11 レビュー R-02）。 */
    @Transactional(readOnly = true)
    public List<QuotedRequestSummary> findQuotedSummaries() {
        return quotationRepository.findLatestOfQuotedRequests();
    }

    /** 受付一覧（S-02）の経路設計中の表。経路設計中の見積依頼ごとの詳細設計依頼済みの見積りを、依頼時刻の古い順に返す（Bolt 12）。 */
    @Transactional(readOnly = true)
    public List<RoutingRequestedSummary> findRoutingRequestedSummaries() {
        return quotationRepository.findRoutingRequestedSummaries();
    }
}
