package com.example.cargotracker.tracking.infrastructure.persistence;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 主要実績の一意制約の違反を制約の名前で分ける（Bolt 39gb の行 61。#50 の P-6）。実績番号の重なりは版の条件の後には単一の流れで起こせないので、マッパーを差し替えて確かめる
 * （出典の重複の違反は PostgreSQL の統合テストで確かめている）。
 */
class MyBatisTrackingRecordRepositoryUniqueKeyTest {

    private final TrackingRecordMapper mapper = mock(TrackingRecordMapper.class);
    private final MyBatisTrackingRecordRepository repository = new MyBatisTrackingRecordRepository(
            mapper, Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneOffset.UTC));

    private TrackingRecord registered() {
        given(mapper.updateTrackingRecord(anyString(), anyLong(), anyString(), any(), any(), any()))
                .willReturn(1);
        given(mapper.findMilestones(anyString())).willReturn(List.of());
        given(mapper.countMilestone(anyString(), anyInt())).willReturn(1);
        return TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord()
                .registerMilestone(
                        MilestoneKind.PICKUP,
                        new Location("JPTYO"),
                        at("2026-11-01T02:00:00Z"),
                        new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-01T02:00:00Z")),
                        new UserId(UUID.randomUUID()),
                        at("2026-11-01T03:00:00Z"))
                .trackingRecord();
    }

    private static DuplicateKeyException violation(String constraint) {
        return new DuplicateKeyException(
                "duplicate", new SQLException("duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    void 行61_出典の重複は競合にする() {
        TrackingRecord trackingRecord = registered();
        willThrow(violation("uk_milestone_source")).given(mapper).insertMilestone(any());

        assertThatThrownBy(() -> repository.update(trackingRecord))
                .isInstanceOf(ConcurrentTrackingRecordUpdateException.class);
    }

    @Test
    void 行61_H2の大文字の制約名でも出典の重複は競合にする() {
        TrackingRecord trackingRecord = registered();
        willThrow(violation("TRACKING.UK_MILESTONE_SOURCE")).given(mapper).insertMilestone(any());

        assertThatThrownBy(() -> repository.update(trackingRecord))
                .isInstanceOf(ConcurrentTrackingRecordUpdateException.class);
    }

    @Test
    void 行61_実績番号の重なりは競合にせず例外のまま上げる() {
        TrackingRecord trackingRecord = registered();
        willThrow(violation("pk_milestone")).given(mapper).insertMilestone(any());

        assertThatThrownBy(() -> repository.update(trackingRecord))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(DuplicateKeyException.class);
    }
}
