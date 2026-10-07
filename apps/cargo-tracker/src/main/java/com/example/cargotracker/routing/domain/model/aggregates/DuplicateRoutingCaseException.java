package com.example.cargotracker.routing.domain.model.aggregates;

import java.util.UUID;

/**
 * 同じ輸送要求版の経路設計案件が先に保存されていた（R-INV-10。DE-16 の同時の再配信）。
 */
public class DuplicateRoutingCaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateRoutingCaseException(UUID transportRequestId, int transportRequestVersionNo) {
        super("輸送要求 " + transportRequestId + " の版 " + transportRequestVersionNo + " の経路設計案件は既にあります");
    }
}
