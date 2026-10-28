package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.AwaitingBookingSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 社内の営業担当者が見積りを照会する入力ポート（S-04）。営業担当者が扱えるのは担当企業の見積依頼だけで（Bolt 38f。US-16 AC4）、
 * 判定は {@link StaffTransportRequestScope} の 1 か所（担当範囲の外は見つからない扱い）。荷主の画面から誤って使わないよう、荷主用の
 * {@link QuotationQueryService} と分ける（ArchUnit の規則が守る）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class StaffQuotationQueryService {

    private final QuotationRepository quotationRepository;
    private final StaffTransportRequestScope scope;

    public StaffQuotationQueryService(QuotationRepository quotationRepository, StaffTransportRequestScope scope) {
        this.quotationRepository = quotationRepository;
        this.scope = scope;
    }

    private Optional<TransportRequest> inScope(TransportRequestNumber number, AuthenticatedActor actor) {
        return scope.find(number, actor) instanceof StaffTransportRequestLookup.Found(var request)
                ? Optional.of(request)
                : Optional.empty();
    }

    /** 見積依頼の、作成中・承認待ち・提示済みの見積り（1 つだけ。Q-INV-18）。算出した後に画面を離れても戻れるようにする（R-04）。 */
    @Transactional(readOnly = true)
    public Optional<Quotation> findActive(TransportRequestNumber number, AuthenticatedActor actor) {
        return inScope(number, actor)
                .flatMap(request -> quotationRepository.findByTransportRequestId(request.id()).stream()
                        .filter(Quotation::isActive)
                        .max(Comparator.comparingInt(Quotation::quotationNo)));
    }

    /** 業務番号と見積り番号で見積りを照会する。 */
    @Transactional(readOnly = true)
    public Optional<Quotation> find(TransportRequestNumber number, int quotationNo, AuthenticatedActor actor) {
        return inScope(number, actor)
                .flatMap(request -> quotationRepository.findByTransportRequestIdAndNo(request.id(), quotationNo));
    }

    /** 見積依頼の見積りを、見積り番号の順にすべて返す（置換先の見積り番号を示すため。Bolt 11）。見積依頼がなければ空。 */
    @Transactional(readOnly = true)
    public List<Quotation> findAll(TransportRequestNumber number, AuthenticatedActor actor) {
        return inScope(number, actor)
                .map(request -> quotationRepository.findByTransportRequestId(request.id()))
                .orElse(List.of());
    }

    /** 受付一覧（S-02）の見積提示済みの表。担当企業の見積依頼ごとの最新の見積りを、有効期限の近い順に返す（Bolt 11 レビュー R-02）。 */
    @Transactional(readOnly = true)
    public List<QuotedRequestSummary> findQuotedSummaries(AuthenticatedActor actor) {
        return scope.listWithin(actor, quotationRepository::findLatestOfQuotedRequests);
    }

    /** 受付一覧（S-02）の経路設計中の表。担当企業の詳細設計依頼済みの見積りを、依頼時刻の古い順に返す（Bolt 12）。 */
    @Transactional(readOnly = true)
    public List<RoutingRequestedSummary> findRoutingRequestedSummaries(AuthenticatedActor actor) {
        return scope.listWithin(actor, quotationRepository::findRoutingRequestedSummaries);
    }

    /** 受付一覧（S-02）の予約の確定待ちの表。担当企業の荷主が承認した見積りを有効期限の近い順に返す（US-04、D-78。Bolt 23b）。 */
    @Transactional(readOnly = true)
    public List<AwaitingBookingSummary> findAwaitingBookingSummaries(AuthenticatedActor actor) {
        return scope.listWithin(actor, quotationRepository::findAwaitingBookingSummaries);
    }
}
