package com.example.cargotracker.routing.domain.model.aggregates;

/**
 * 航海を読み込んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合。Bolt 39e）。
 */
public class ConcurrentVoyageUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConcurrentVoyageUpdateException(String voyageNumber, long expectedVersion) {
        super("航海 " + voyageNumber + " は版 " + expectedVersion + " の後に更新されています");
    }
}
