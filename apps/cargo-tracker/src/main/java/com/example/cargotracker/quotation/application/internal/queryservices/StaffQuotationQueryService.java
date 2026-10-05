package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
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

    /** 業務番号と見積り番号で見積りを照会する。 */
    @Transactional(readOnly = true)
    public Optional<Quotation> find(TransportRequestNumber number, int quotationNo) {
        return transportRequestRepository
                .findByNumberForStaff(number)
                .flatMap(request -> quotationRepository.findByTransportRequestIdAndNo(request.id(), quotationNo));
    }
}
