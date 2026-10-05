package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 社内の営業担当者が輸送要求を照会する入力ポート（S-02・S-03）。営業担当者はすべての荷主の輸送要求を扱うため、荷主企業で絞らない。
 * 荷主の画面から誤って使わないよう、荷主用の {@link TransportRequestQueryService} と分ける（Bolt 5 レビュー R-10）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class StaffTransportRequestQueryService {

    private final TransportRequestRepository repository;
    private final RequiredDocumentStorage documentStorage;

    public StaffTransportRequestQueryService(
            TransportRequestRepository repository, RequiredDocumentStorage documentStorage) {
        this.repository = repository;
        this.documentStorage = documentStorage;
    }

    /** 業務番号で輸送要求を照会する。 */
    @Transactional(readOnly = true)
    public Optional<TransportRequest> findByNumber(TransportRequestNumber number) {
        return repository.findByNumberForStaff(number);
    }

    /** 審査中の輸送要求を、最初の提出時刻の古い順に一覧する（受付一覧）。 */
    @Transactional(readOnly = true)
    public List<TransportRequestSummary> findUnderReview() {
        return repository.findUnderReviewSummaries();
    }

    /** 必要書類を取得する（D-20: 営業担当者は開ける）。 */
    @Transactional(readOnly = true)
    public Optional<DocumentFile> findDocument(TransportRequestNumber number, int versionNo, int documentNo) {
        return repository
                .findByNumberForStaff(number)
                .flatMap(request -> request.document(versionNo, documentNo))
                .map(document -> new DocumentFile(document, documentStorage.read(document.objectKey())));
    }
}
