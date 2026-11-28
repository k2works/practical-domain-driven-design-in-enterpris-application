package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;

/**
 * 経路設計案件を読み込んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合）。
 */
public class ConcurrentRoutingCaseUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConcurrentRoutingCaseUpdateException(RoutingCaseNumber number, long expectedVersion) {
        super("経路設計案件 " + number.text() + " は版 " + expectedVersion + " の後に更新されています");
    }
}
