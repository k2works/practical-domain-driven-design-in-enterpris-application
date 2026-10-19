package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AwaitingBookingSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private final TransportRequestRepository transportRequestRepository;
    private final QuotationRepository quotationRepository;

    public StaffQuotationQueryService(
            TransportRequestRepository transportRequestRepository, QuotationRepository quotationRepository) {
        this.transportRequestRepository = transportRequestRepository;
        this.quotationRepository = quotationRepository;
    }

    /**
     * 見積りを再見積りできないなら、その理由（D-85、Q-INV-07。Bolt 35c）。S-04 の再見積りのボタン・GET と、再見積りのコマンド（POST）が
     * 同じ順で同じ理由を示すよう、判定をここにまとめる。順は、輸送要求の状態（予約確定済み → 再見積りできる状態でない）→ 輸送要求の
     * 版 → 見積りの判定時刻の判定（{@link Quotation#requoteRejectionAt}）。引数だけで決まり、リポジトリを読まない。
     *
     * @param request 見積りの輸送要求
     * @param quotation 再見積りの旧版
     * @param at 判定時刻
     * @return 再見積りできない理由（再見積りできるなら空）
     */
    public Optional<QuotationRejection> requoteRejection(TransportRequest request, Quotation quotation, UtcInstant at) {
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

    /** 受付一覧（S-02）の予約の確定待ちの表。荷主が承認した見積りを有効期限の近い順に返す（US-04、Bolt 20 レビュー D-78。Bolt 23b）。 */
    @Transactional(readOnly = true)
    public List<AwaitingBookingSummary> findAwaitingBookingSummaries() {
        return quotationRepository.findAwaitingBookingSummaries();
    }
}
