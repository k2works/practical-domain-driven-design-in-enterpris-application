package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
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

    /** 導出し直した現在状態と根拠の実績番号を書き、版を 1 増やす（Bolt 26b）。読み込んだときの版と違えば 0 件。 */
    int updateTrackingRecord(
            @Param("trackingNumber") String trackingNumber,
            @Param("expectedVersion") long expectedVersion,
            @Param("currentStatus") String currentStatus,
            @Param("statusBasisMilestoneNo") Integer statusBasisMilestoneNo,
            @Param("updatedAt") OffsetDateTime updatedAt);

    void insertMilestone(MilestoneRow row);

    /** 主要実績を実績番号の順に（Bolt 26b）。 */
    List<MilestoneRow> findMilestones(@Param("trackingNumber") String trackingNumber);

    Optional<TrackingRecordRow> findTrackingRecordByBookingId(@Param("bookingId") UUID bookingId);

    Optional<TrackingRecordRow> findTrackingRecordByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    /** C-10 照会の結果（Bolt 27）。追跡番号と荷主企業で絞る。 */
    Optional<TrackingRecordRow> findTrackingRecordByTrackingNumberAndShipper(
            @Param("trackingNumber") String trackingNumber, @Param("shipperCompanyId") UUID shipperCompanyId);

    /** C-10 追跡の照会の一覧（Bolt 27）。荷主企業で絞り、追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで。 */
    List<TrackingRecordSummaryRow> findRecentSummariesByShipper(
            @Param("shipperCompanyId") UUID shipperCompanyId, @Param("limit") int limit);

    /** S-11 追跡一覧（Bolt 26）。追跡の開始時刻の新しい順（同じ時刻なら追跡番号の順）に上限まで。 */
    List<TrackingRecordSummaryRow> findRecentSummaries(@Param("limit") int limit);

    List<ScheduledLegRow> findScheduledLegs(@Param("trackingNumber") String trackingNumber);
}
