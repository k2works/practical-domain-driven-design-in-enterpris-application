package com.example.cargotracker.tracking.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordDetail;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * 鮮度の判定の許容範囲が設定 {@code cargotracker.tracking.freshness-tolerance} から照会サービスまで届く（FRS-02。Bolt 39g4 の行 8。前例
 * {@code InfoFreshnessSettingIntegrationTest}）。区間 1 の出発予定を今の 40 時間前にした追跡を開始して保存し（H2。追跡の開始の INSERT が次の予定の実績の
 * 予定時刻を書く）、照会サービスで S-12 の判定を引く。予定は判定と同じ JVM の時計から作る（T-171）。トランザクションは戻す。入れ子のクラスは
 * {@code @TestPropertySource} が加わるので別のコンテキストで PT48H を読む。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TrackingFreshnessSettingIntegrationTest {

    static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000039404"));

    @Autowired
    TrackingRecordQueryService queryService;

    @Autowired
    TrackingRecordRepository repository;

    @Test
    void 既定の36時間では出発予定から40時間たった追跡は鮮度超過() {
        TrackingRecordDetail detail = detailOfStartedForty();

        assertThat(detail.freshnessExceeded()).isTrue();
        assertThat(detail.freshnessTolerance()).isEqualTo(Duration.ofHours(36));
    }

    @Nested
    @TestPropertySource(properties = "cargotracker.tracking.freshness-tolerance=PT48H")
    class 許容範囲を48時間にすると {

        @Test
        void 出発予定から40時間たった追跡は鮮度超過でない() {
            assertThat(TestTransaction.isActive()).isTrue();

            TrackingRecordDetail detail = detailOfStartedForty();

            assertThat(detail.freshnessExceeded()).isFalse();
            assertThat(detail.freshnessTolerance()).isEqualTo(Duration.ofHours(48));
        }
    }

    TrackingRecordDetail detailOfStartedForty() {
        Instant departure = Instant.now().minus(Duration.ofHours(40)).truncatedTo(ChronoUnit.SECONDS);
        TrackingNumber trackingNumber = new TrackingNumber("CTFRESHGGGG394");
        Schedule schedule = new Schedule(
                "RC-2026-0001",
                1,
                List.of(new ScheduledLeg(
                        "V100",
                        new Location("JPTYO"),
                        new Location("USLAX"),
                        new UtcInstant(departure),
                        new UtcInstant(departure.plus(Duration.ofDays(10))))));
        TrackingRecord started = TrackingRecord.start(
                        trackingNumber,
                        UUID.randomUUID(),
                        SHIPPER,
                        new CompanyId(UUID.randomUUID()),
                        schedule,
                        new UtcInstant(departure.minus(Duration.ofDays(1))))
                .trackingRecord();
        repository.save(started);
        AuthenticatedActor manager =
                AuthenticatedActorFixture.trackingManagerHandling(new UserId(UUID.randomUUID()), SHIPPER);

        return queryService.detailWithFreshness(trackingNumber, manager).orElseThrow();
    }
}
