package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MissingIngestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");
    private static final Instant START_OF_DAY = Instant.parse("2026-11-26T15:00:00Z");

    private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final MissingIngestionQueryService service =
            new MissingIngestionQueryService(dataSources, ingestions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 承認済みの情報源があり当日の取込がなければ不足() {
        given(dataSources.countApproved()).willReturn(1);
        given(ingestions.existsSince(START_OF_DAY)).willReturn(false);

        assertThat(service.isTodayMissing()).isTrue();
    }

    @Test
    void 当日の取込があれば不足でない() {
        given(dataSources.countApproved()).willReturn(2);
        given(ingestions.existsSince(START_OF_DAY)).willReturn(true);

        assertThat(service.isTodayMissing()).isFalse();
    }

    @Test
    void 承認済みの情報源がなければ取込を調べず不足にしない() {
        given(dataSources.countApproved()).willReturn(0);

        assertThat(service.isTodayMissing()).isFalse();
        verify(ingestions, never()).existsSince(any());
    }
}
