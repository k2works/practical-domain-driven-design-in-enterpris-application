package com.example.cargotracker.tracking.application.internal.queryservices;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 荷主の追跡の照会（C-10。BR-07、T-INV-09。Bolt 27）。 */
class CustomerTrackingQueryServiceTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002701"));
    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002702"));
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final InMemoryTrackingRecordRepository repository = new InMemoryTrackingRecordRepository();
    private final CustomerTrackingQueryService service = new CustomerTrackingQueryService(repository);

    @Test
    void 自社の追跡番号は荷主向けの照会結果を返す() {
        save(number(1), SHIPPER, "2026-10-26T01:00:00Z");

        assertThat(service.find(number(1), SHIPPER))
                .hasValueSatisfying(view -> assertThat(view.trackingNumber()).isEqualTo(number(1)));
    }

    @Test
    void 他社の追跡番号は存在しない追跡番号と同じく空を返す() {
        save(number(1), OTHER_SHIPPER, "2026-10-26T01:00:00Z");

        assertThat(service.find(number(1), SHIPPER)).isEmpty();
        assertThat(service.find(number(2), SHIPPER)).isEmpty();
    }

    @Test
    void 一覧は自社の追跡記録だけを追跡の開始の新しい順に返し最新の到着見込みを持つ() {
        save(number(1), SHIPPER, "2026-10-26T01:00:00Z");
        save(number(2), OTHER_SHIPPER, "2026-10-26T02:00:00Z");
        save(number(3), SHIPPER, "2026-10-26T03:00:00Z");

        RecentTrackingRecords recent = service.recent(SHIPPER);

        assertThat(recent.rows())
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(number(3), number(1));
        assertThat(recent.rows().getFirst().latestEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
        assertThat(recent.truncated()).isFalse();
        assertThat(recent.limit()).isEqualTo(50);
    }

    @Test
    void 一覧は上限の50件を超えたら超えたことを示す() {
        for (int i = 0; i < 51; i++) {
            save(number(i), SHIPPER, "2026-10-26T01:00:00Z");
        }

        RecentTrackingRecords recent = service.recent(SHIPPER);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isTrue();
    }

    @Test
    void 一覧はちょうど50件なら超えていない() {
        for (int i = 0; i < 50; i++) {
            save(number(i), SHIPPER, "2026-10-26T01:00:00Z");
        }

        assertThat(service.recent(SHIPPER).truncated()).isFalse();
    }

    private void save(TrackingNumber trackingNumber, CompanyId shipper, String startedAt) {
        repository.save(TrackingRecord.start(
                        trackingNumber,
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        schedule(),
                        at(startedAt))
                .trackingRecord());
    }

    private static TrackingNumber number(int no) {
        return new TrackingNumber(
                "CTAAAAAAAAAA" + ALPHABET.charAt(no / ALPHABET.length()) + ALPHABET.charAt(no % ALPHABET.length()));
    }
}
