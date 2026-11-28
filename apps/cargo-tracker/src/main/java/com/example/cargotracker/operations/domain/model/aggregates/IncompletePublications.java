package com.example.cargotracker.operations.domain.model.aggregates;

import java.time.Instant;
import java.util.List;

/** イベントの発行記録のうち、完了していないものを読むポート。 */
@FunctionalInterface
public interface IncompletePublications {

    /**
     * @param before この時刻より前に発行されたものに限る
     * @return 未完了の発行の時刻
     */
    List<Instant> publishedBefore(Instant before);
}
