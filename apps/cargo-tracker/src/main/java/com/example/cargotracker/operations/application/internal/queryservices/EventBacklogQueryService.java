package com.example.cargotracker.operations.application.internal.queryservices;

import com.example.cargotracker.operations.domain.model.aggregates.IncompletePublications;
import com.example.cargotracker.operations.domain.model.rules.EventBacklogPolicy;
import com.example.cargotracker.operations.domain.model.valueobjects.EventBacklog;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 未完了のイベント配信の滞留を照会する（OBS-03）。 */
@Service
public class EventBacklogQueryService {

    private final IncompletePublications publications;
    private final Clock clock;

    public EventBacklogQueryService(IncompletePublications publications, Clock clock) {
        this.publications = publications;
        this.clock = clock;
    }

    public EventBacklog current() {
        Instant now = clock.instant();
        // 滞留になりうる発行だけを読む。境界の 1 件を落とさないよう 1 ミリ秒だけ広げ、最後の判定は規則に任せる
        Instant before = now.minus(EventBacklogPolicy.STALE_AFTER).plusMillis(1);
        return EventBacklogPolicy.assess(publications.publishedBefore(before), now);
    }
}
