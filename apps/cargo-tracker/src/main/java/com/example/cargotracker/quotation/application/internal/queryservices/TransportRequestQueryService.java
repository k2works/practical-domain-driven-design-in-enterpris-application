package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主が自社の輸送要求を照会する入力ポート（C-02・C-04）。照会は必ず荷主企業で絞る（Q-INV-08）。
 * 社内の営業担当者の照会は {@link StaffTransportRequestQueryService} を使う。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class TransportRequestQueryService {

    private final TransportRequestRepository repository;
    private final RequiredDocumentStorage documentStorage;

    public TransportRequestQueryService(
            TransportRequestRepository repository, RequiredDocumentStorage documentStorage) {
        this.repository = repository;
        this.documentStorage = documentStorage;
    }

    @Transactional(readOnly = true)
    public Optional<TransportRequest> findById(TransportRequestId id) {
        return repository.findById(id);
    }

    /** 荷主企業の輸送要求を業務番号で照会する（画面には内部の ID でなく業務番号を出す。D-4）。他社の輸送要求は見つからない。 */
    @Transactional(readOnly = true)
    public Optional<TransportRequest> findByNumber(TransportRequestNumber number, CompanyId shipperCompanyId) {
        return repository.findByNumber(number, shipperCompanyId);
    }

    /** 荷主企業の輸送要求を、最初の提出時刻の新しい順に一覧する（見積依頼の一覧）。 */
    @Transactional(readOnly = true)
    public List<TransportRequestSummary> findSummaries(CompanyId shipperCompanyId) {
        return repository.findSummariesByShipper(shipperCompanyId);
    }

    /**
     * 荷主企業の輸送要求の必要書類を取得する（D-20: 提出した荷主は開ける）。他社の輸送要求の書類は見つからない（Q-INV-08）。
     */
    @Transactional(readOnly = true)
    public Optional<DocumentFile> findDocument(
            TransportRequestNumber number, CompanyId shipperCompanyId, int versionNo, int documentNo) {
        return repository
                .findByNumber(number, shipperCompanyId)
                .flatMap(request -> documentOf(request, versionNo, documentNo, documentStorage));
    }

    /** 輸送要求の版の書類を探し、中身を読む。現在の版の書類だけを返す（前の版の書類は、引き継いでいれば現在の版にある）。 */
    static Optional<DocumentFile> documentOf(
            TransportRequest request, int versionNo, int documentNo, RequiredDocumentStorage storage) {
        if (request.currentVersion().versionNo() != versionNo) {
            return Optional.empty();
        }
        return request.currentVersion().terms().documents().stream()
                .filter(document -> document.documentNo() == documentNo)
                .findFirst()
                .map(document -> new DocumentFile(document, storage.read(document.objectKey())));
    }
}
