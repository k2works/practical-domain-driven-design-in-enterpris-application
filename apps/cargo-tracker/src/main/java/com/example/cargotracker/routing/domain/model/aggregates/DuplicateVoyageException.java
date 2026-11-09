package com.example.cargotracker.routing.domain.model.aggregates;

/**
 * 同じ航海番号の航海が先に保存されていた（初めての航海の DE-12 の同時。Bolt 39e）。
 */
public class DuplicateVoyageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateVoyageException(String voyageNumber) {
        super("航海 " + voyageNumber + " は既にあります");
    }
}
