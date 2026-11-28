package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;

/**
 * 同じ輸送要求の同じ見積り番号の見積りが、先に保存されていた（同時の算出。UK `uk_quotation_request_no`）。
 * 入力ポートは「見積りがすでにある」（Q-INV-18）として値で返す（Bolt 9・10 レビュー R-02）。
 */
public class DuplicateQuotationException extends RuntimeException {

    public DuplicateQuotationException(TransportRequestId transportRequestId, int quotationNo) {
        super("輸送要求 " + transportRequestId.value() + " の見積り " + quotationNo + " は先に保存されていました");
    }
}
