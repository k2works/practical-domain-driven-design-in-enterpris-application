package com.example.cargotracker.tracking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 社内の追跡記録の担当範囲の判定（Bolt 38g。US-16 AC4、IA-INV-02）。 */
class TrackingRecordScopeTest {

    private static final CompanyId OTHER = new CompanyId(UUID.randomUUID());

    private final InMemoryTrackingRecordRepository repository = new InMemoryTrackingRecordRepository();
    private final TrackingRecordScope scope = new TrackingRecordScope(repository);

    @Test
    void 担当企業の追跡記録は見つかる() {
        TrackingRecord saved = saved();

        assertThat(scope.find(saved.trackingNumber(), manager(TrackingFixture.SHIPPER))
                        .inScope())
                .map(TrackingRecord::trackingNumber)
                .contains(saved.trackingNumber());
    }

    @Test
    void 担当企業の外の追跡記録は範囲の外() {
        TrackingRecord saved = saved();

        assertThat(scope.find(saved.trackingNumber(), manager(OTHER))).isEqualTo(new TrackingRecordLookup.OutOfScope());
        assertThat(scope.find(saved.trackingNumber(), manager())).isEqualTo(new TrackingRecordLookup.OutOfScope());
    }

    @Test
    void 追跡番号の追跡記録がなければ見つからない() {
        assertThat(scope.find(new TrackingNumber("CTZZZZZZZZ2345"), manager(TrackingFixture.SHIPPER)))
                .isEqualTo(new TrackingRecordLookup.NotFound());
    }

    @Test
    void 範囲の外とないは範囲の中の追跡記録を持たない() {
        assertThat(new TrackingRecordLookup.OutOfScope().inScope()).isEmpty();
        assertThat(new TrackingRecordLookup.NotFound().inScope()).isEmpty();
    }

    @Test
    void 一覧は担当企業の集合で読む() {
        TrackingRecordRepository mocked = mock(TrackingRecordRepository.class);
        when(mocked.findRecentSummaries(any(), anyInt())).thenReturn(List.of());

        new TrackingRecordScope(mocked)
                .listWithin(
                        manager(TrackingFixture.SHIPPER, OTHER), companies -> mocked.findRecentSummaries(companies, 3));

        verify(mocked).findRecentSummaries(Set.of(TrackingFixture.SHIPPER, OTHER), 3);
    }

    @Test
    void 担当企業がなければ一覧を読まずに空を返す() {
        // 行 6（空の守り。IN () は書けない）
        TrackingRecordRepository mocked = mock(TrackingRecordRepository.class);

        assertThat(new TrackingRecordScope(mocked)
                        .listWithin(manager(), companies -> mocked.findRecentSummaries(companies, 3)))
                .isEmpty();
        verify(mocked, never()).findRecentSummaries(any(), anyInt());
    }

    private TrackingRecord saved() {
        TrackingRecord trackingRecord = TrackingRecord.start(
                        TrackingFixture.TRACKING_NUMBER,
                        TrackingFixture.BOOKING_ID,
                        TrackingFixture.SHIPPER,
                        TrackingFixture.CONSIGNEE,
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord();
        repository.save(trackingRecord);
        return trackingRecord;
    }

    private static AuthenticatedActor manager(CompanyId... shippers) {
        return AuthenticatedActorFixture.trackingManagerHandling(new UserId(UUID.randomUUID()), shippers);
    }
}
