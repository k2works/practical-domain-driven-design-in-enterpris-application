package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求を照会する入力ポート。
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

    /** 社内用: 業務番号で輸送要求を照会する。営業担当者はすべての荷主の輸送要求を扱うため、荷主企業で絞らない。 */
    @Transactional(readOnly = true)
    public Optional<TransportRequest> findByNumberForStaff(TransportRequestNumber number) {
        return repository.findByNumberForStaff(number);
    }

    /** 社内用: 審査中の輸送要求を、提出時刻の古い順に一覧する（S-02）。 */
    @Transactional(readOnly = true)
    public List<TransportRequest> findUnderReview() {
        return repository.findUnderReview();
    }
}
