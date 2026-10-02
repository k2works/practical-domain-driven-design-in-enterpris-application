package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Optional;

/**
 * 輸送要求のリポジトリ（送信ポート）。実装は infrastructure に置く。
 */
public interface TransportRequestRepository {

    void save(TransportRequest transportRequest);

    Optional<TransportRequest> findById(TransportRequestId id);

    /**
     * 荷主企業の輸送要求を業務番号で探す。業務番号は一意なので、見つかるのは高々 1 件。
     * 業務番号は連番で推測しやすいため、必ず荷主企業で絞る（他社の輸送要求は見つからない。Q-INV-08、Bolt 4 レビュー R-02）。
     */
    Optional<TransportRequest> findByNumber(TransportRequestNumber number, CompanyId shipperCompanyId);
}
