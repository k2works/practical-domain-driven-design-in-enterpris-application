package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.rules.IngestionCadence;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** 当日の取込が足りないかを照会する（FRS-04。Bolt 58）。承認済みの情報源がなければ、取り込むものがないので不足にしない。 */
@Service
public class MissingIngestionQueryService {

    private final DataSourceRepository dataSources;
    private final IngestionRepository ingestions;
    private final Clock clock;

    public MissingIngestionQueryService(DataSourceRepository dataSources, IngestionRepository ingestions, Clock clock) {
        this.dataSources = dataSources;
        this.ingestions = ingestions;
        this.clock = clock;
    }

    public boolean isTodayMissing() {
        return dataSources.countApproved() > 0 && !ingestions.existsSince(IngestionCadence.startOfDay(clock.instant()));
    }
}
