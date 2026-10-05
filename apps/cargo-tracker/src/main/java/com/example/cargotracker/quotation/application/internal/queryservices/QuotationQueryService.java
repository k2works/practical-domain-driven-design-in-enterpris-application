package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主が自社の見積依頼の見積りを照会する入力ポート（C-04。US-03 AC2）。照会は必ず荷主企業で絞る（Q-INV-08）。
 * 荷主に見せるのは提示した見積り（提示済みと、提示した後に置換済み・失効になったもの）だけで、社内承認の前の見積りは見せない。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationQueryService {

    private final TransportRequestRepository transportRequestRepository;
    private final QuotationRepository quotationRepository;

    public QuotationQueryService(
            TransportRequestRepository transportRequestRepository, QuotationRepository quotationRepository) {
        this.transportRequestRepository = transportRequestRepository;
        this.quotationRepository = quotationRepository;
    }

    /**
     * 荷主企業の見積依頼の、提示した見積りを見積り番号の新しい順に返す（C-04。最新と読み取り専用の旧版。US-03 AC5。Bolt 11）。
     * 提示する前に置き換えた見積りと、承認待ちの見積りは含めない。他社の見積依頼の見積りは見つからない。
     */
    @Transactional(readOnly = true)
    public List<Quotation> findVisible(TransportRequestNumber number, CompanyId shipperCompanyId) {
        return transportRequestRepository
                .findByNumber(number, shipperCompanyId)
                .map(request -> quotationRepository.findByTransportRequestId(request.id()).stream()
                        .filter(quotation -> quotation.presentedAt().isPresent())
                        .sorted(Comparator.comparingInt(Quotation::quotationNo).reversed())
                        .toList())
                .orElse(List.of());
    }
}
