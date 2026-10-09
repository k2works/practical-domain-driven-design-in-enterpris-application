package com.example.cargotracker.tracking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 追跡の表（`tracking` スキーマ）の MyBatis のマッパー（Bolt 25。照会は Bolt 26）。SQL は自分のスキーマだけを参照する（AT-04）。
 */
@Mapper
public interface TrackingRecordMapper {

    void insertTrackingRecord(TrackingRecordRow row);

    void insertScheduledLeg(ScheduledLegRow row);

    Optional<TrackingRecordRow> findTrackingRecordByBookingId(@Param("bookingId") UUID bookingId);

    Optional<TrackingRecordRow> findTrackingRecordByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    /** S-11 追跡一覧（Bolt 26）。追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで。 */
    List<TrackingRecordSummaryRow> findRecentSummaries(@Param("limit") int limit);

    List<ScheduledLegRow> findScheduledLegs(@Param("trackingNumber") String trackingNumber);
}
