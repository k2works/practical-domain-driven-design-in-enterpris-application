package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.entities.TransportRequestVersion;
import com.example.cargotracker.quotation.domain.model.valueobjects.Cargo;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の輸送要求リポジトリ。本物と同じ規則で振る舞う。
 * <ul>
 *   <li>同じ ID の輸送要求を 2 回保存（新規）すると失敗する
 *   <li>読み出すたびに保存した時点の写しを返し、更新は読み込んだときの集約の版で照合する（楽観ロック）
 *   <li>荷主の照会は荷主企業で絞り、社内用の照会は絞らない。荷主の一覧は新しい順、受付一覧は古い順に並べる
 * </ul>
 */
public class InMemoryTransportRequestRepository implements TransportRequestRepository {

    private final Map<TransportRequestId, TransportRequest> store = new ConcurrentHashMap<>();

    /** 最初の提出時刻（版 1）。本物は版 1 の行から読むが、集約は現在の版しか持たないため、保存のときに覚える。 */
    private final Map<TransportRequestId, UtcInstant> firstSubmittedAt = new ConcurrentHashMap<>();

    @Override
    public void save(TransportRequest transportRequest) {
        if (store.putIfAbsent(transportRequest.id(), snapshot(transportRequest, transportRequest.aggregateVersion()))
                != null) {
            throw new IllegalStateException("輸送要求は既に保存されています: " + transportRequest.id());
        }
        firstSubmittedAt.put(
                transportRequest.id(), transportRequest.currentVersion().submittedAt());
    }

    @Override
    public synchronized void update(TransportRequest transportRequest) {
        TransportRequest stored = store.get(transportRequest.id());
        if (stored == null || stored.aggregateVersion() != transportRequest.aggregateVersion()) {
            throw new ConcurrentTransportRequestUpdateException(
                    transportRequest.id(), transportRequest.aggregateVersion());
        }
        store.put(transportRequest.id(), snapshot(transportRequest, transportRequest.aggregateVersion() + 1));
    }

    @Override
    public Optional<TransportRequest> findById(TransportRequestId id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryTransportRequestRepository::copy);
    }

    @Override
    public Optional<TransportRequest> findByNumber(TransportRequestNumber number, CompanyId shipperCompanyId) {
        return findByNumberForStaff(number)
                .filter(request -> request.shipperCompanyId().equals(shipperCompanyId));
    }

    @Override
    public Optional<TransportRequest> findByNumberForStaff(TransportRequestNumber number) {
        return store.values().stream()
                .filter(request -> request.number().equals(number))
                .findFirst()
                .map(InMemoryTransportRequestRepository::copy);
    }

    @Override
    public List<TransportRequestSummary> findSummariesByShipper(CompanyId shipperCompanyId) {
        return store.values().stream()
                .filter(request -> request.shipperCompanyId().equals(shipperCompanyId))
                .map(this::toSummary)
                .sorted(byFirstSubmittedAt().reversed())
                .toList();
    }

    @Override
    public List<TransportRequestSummary> findUnderReviewSummaries() {
        return store.values().stream()
                .filter(request -> request.status() == TransportRequestStatus.UNDER_REVIEW)
                .map(this::toSummary)
                .sorted(byFirstSubmittedAt())
                .toList();
    }

    @Override
    public List<TransportRequestSummary> findQuotingSummaries() {
        return store.values().stream()
                .filter(request -> request.status() == TransportRequestStatus.QUOTING)
                .map(this::toSummary)
                .sorted(byFirstSubmittedAt())
                .toList();
    }

    private TransportRequestSummary toSummary(TransportRequest request) {
        return new TransportRequestSummary(
                request.number(),
                request.currentVersion().versionNo(),
                request.status(),
                firstSubmittedAt.get(request.id()),
                request.currentVersion().submittedAt(),
                request.currentVersion().terms().origin(),
                request.currentVersion().terms().destination(),
                request.currentVersion().terms().arrivalDeadline(),
                request.currentVersion().terms().cargo().category());
    }

    private static Comparator<TransportRequestSummary> byFirstSubmittedAt() {
        return Comparator.comparing((TransportRequestSummary summary) ->
                        summary.firstSubmittedAt().instant())
                .thenComparing(summary -> summary.number().text());
    }

    /** 保存した輸送要求の件数。提出を受け付けなかったときに何も保存していないことを確かめる。 */
    public int count() {
        return store.size();
    }

    /**
     * 提出の規則の外で受け付けた輸送要求（規則ができる前の受け付けや手作業の登録）を作るため、現在の版の貨物種別を置き換える（Bolt 35。
     * 予約の判定が見積りの受付の規則に頼らずに特殊貨物を拒むことを確かめる。US-04 AC5、B-INV-04）。集約の版は変えない。見積りの公開 API が
     * 読む現在の版だけを変え、ほかのコンテキストの写し（経路設計の案件の貨物種別など）は変えないので、確定の時点の判定だけを確かめる
     * テスト専用の状態として使う。
     */
    public synchronized void replaceCargoCategory(TransportRequestId id, CargoCategory category) {
        TransportRequest stored = store.get(id);
        if (stored == null) {
            throw new IllegalStateException("輸送要求がありません: " + id);
        }
        TransportRequestVersion version = stored.currentVersion();
        ShipmentTerms terms = version.terms();
        Cargo cargo = terms.cargo();
        ShipmentTerms replaced = new ShipmentTerms(
                terms.consigneeCompanyId(),
                terms.origin(),
                terms.destination(),
                terms.arrivalDeadline(),
                new Cargo(category, cargo.packageType(), cargo.packageCount(), cargo.grossWeightKg(), cargo.volumeM3()),
                terms.documents());
        store.put(
                id,
                TransportRequest.reconstitute(
                        stored.id(),
                        stored.number(),
                        stored.shipperCompanyId(),
                        stored.status(),
                        new TransportRequestVersion(
                                version.versionNo(), replaced, version.submittedBy(), version.submittedAt()),
                        stored.reviewRecords(),
                        stored.aggregateVersion()));
    }

    /** シナリオの開始時に記録を消す。 */
    public void clear() {
        store.clear();
        firstSubmittedAt.clear();
    }

    private static TransportRequest copy(TransportRequest request) {
        return snapshot(request, request.aggregateVersion());
    }

    private static TransportRequest snapshot(TransportRequest request, long aggregateVersion) {
        return TransportRequest.reconstitute(
                request.id(),
                request.number(),
                request.shipperCompanyId(),
                request.status(),
                request.currentVersion(),
                request.reviewRecords(),
                aggregateVersion);
    }
}
