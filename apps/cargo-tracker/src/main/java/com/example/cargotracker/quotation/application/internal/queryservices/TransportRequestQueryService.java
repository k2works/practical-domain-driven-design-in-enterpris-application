package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主が自社の輸送要求を照会する入力ポート（C-02・C-04）。照会は必ず荷主企業で絞る（Q-INV-08）。
 * 社内の営業担当者の照会は {@link StaffTransportRequestQueryService} を使う。
 */
public class TransportRequestQueryService {

    private final TransportRequestRepository repository;

    public TransportRequestQueryService(TransportRequestRepository repository) {
        this.repository = repository;
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
}
