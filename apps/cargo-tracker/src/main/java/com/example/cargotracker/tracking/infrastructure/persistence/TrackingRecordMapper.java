package com.example.cargotracker.tracking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 追跡の表（`tracking` スキーマ）の MyBatis のマッパー（Bolt 25）。SQL は自分のスキーマだけを参照する（AT-04）。
 */
@Mapper
public interface TrackingRecordMapper {

    void insertTrackingRecord(TrackingRecordRow row);

    void insertScheduledLeg(ScheduledLegRow row);

    Optional<TrackingRecordRow> findTrackingRecordByBookingId(@Param("bookingId") UUID bookingId);

    List<ScheduledLegRow> findScheduledLegs(@Param("trackingNumber") String trackingNumber);
}
