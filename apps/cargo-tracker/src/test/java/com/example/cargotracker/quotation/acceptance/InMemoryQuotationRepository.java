package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の見積りリポジトリ。本物と同じ規則で振る舞う。
 * <ul>
 *   <li>同じ ID の見積りや、同じ輸送要求の同じ見積り番号を 2 回保存（新規）すると失敗する（UK）
 *   <li>同じ輸送要求に作成中・承認待ち・提示済み・詳細設計依頼済みの見積りを 2 つ保存すると失敗する（部分一意インデックス。Q-INV-18。Bolt 11・12）
 *   <li>読み出すたびに保存した時点の写しを返し、更新は読み込んだときの集約の版で照合する（楽観ロック）
 * </ul>
 */
public class InMemoryQuotationRepository implements QuotationRepository {

    private final Map<QuotationId, Quotation> store = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Quotation quotation) {
        boolean duplicatedNo = store.values().stream()
                .anyMatch(stored -> stored.transportRequestId().equals(quotation.transportRequestId())
                        && stored.quotationNo() == quotation.quotationNo());
        if (store.containsKey(quotation.id())) {
            throw new IllegalStateException("見積りは既に保存されています: " + quotation.id());
        }
        boolean duplicatedActive = quotation.isActive()
                && store.values().stream()
                        .anyMatch(stored -> stored.transportRequestId().equals(quotation.transportRequestId())
                                && stored.isActive());
        if (duplicatedNo || duplicatedActive) {
            throw new DuplicateQuotationException(quotation.transportRequestId(), quotation.quotationNo());
        }
        store.put(quotation.id(), snapshot(quotation, quotation.aggregateVersion()));
    }

    @Override
    public synchronized void update(Quotation quotation) {
        Quotation stored = store.get(quotation.id());
        if (stored == null || stored.aggregateVersion() != quotation.aggregateVersion()) {
            throw new ConcurrentQuotationUpdateException(quotation.id(), quotation.aggregateVersion());
        }
        store.put(quotation.id(), snapshot(quotation, quotation.aggregateVersion() + 1));
    }

    @Override
    public List<Quotation> findByTransportRequestId(TransportRequestId transportRequestId) {
        return store.values().stream()
                .filter(quotation -> quotation.transportRequestId().equals(transportRequestId))
                .sorted(Comparator.comparingInt(Quotation::quotationNo))
                .map(quotation -> snapshot(quotation, quotation.aggregateVersion()))
                .toList();
    }

    @Override
    public Optional<Quotation> findByTransportRequestIdAndNo(TransportRequestId transportRequestId, int quotationNo) {
        return findByTransportRequestId(transportRequestId).stream()
                .filter(quotation -> quotation.quotationNo() == quotationNo)
                .findFirst();
    }

    /** 輸送要求の状態を知らないため、業務ルール層では使わない（PostgreSQL の統合テストで確かめる。Bolt 11 レビュー R-02）。 */
    @Override
    public List<QuotedRequestSummary> findLatestOfQuotedRequests() {
        throw new UnsupportedOperationException("見積提示済みの一覧は業務ルール層で使わない");
    }

    /** 輸送要求の状態を知らないため、業務ルール層では使わない（PostgreSQL の統合テストで確かめる。Bolt 12）。 */
    @Override
    public List<RoutingRequestedSummary> findRoutingRequestedSummaries() {
        throw new UnsupportedOperationException("経路設計中の一覧は業務ルール層で使わない");
    }

    public void clear() {
        store.clear();
    }

    private static Quotation snapshot(Quotation quotation, long aggregateVersion) {
        return Quotation.reconstitute(
                quotation.id(),
                quotation.transportRequestId(),
                quotation.quotationNo(),
                quotation.transportRequestVersionNo(),
                quotation.status(),
                quotation.pricingBasis().orElse(null),
                quotation.expiry().orElse(null),
                quotation.routePolicy().orElse(null),
                quotation.approvedBy().orElse(null),
                quotation.presentedAt().orElse(null),
                quotation.replacedBy().orElse(null),
                quotation.respondedBy().orElse(null),
                quotation.respondedAt().orElse(null),
                quotation.assignedRoute().orElse(null),
                quotation.shipperApproval().orElse(null),
                aggregateVersion);
    }
}
