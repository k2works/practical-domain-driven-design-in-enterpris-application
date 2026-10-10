package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 貨物予約・予約版・予約サガ・処理済みコマンドの表の MyBatis マッパー。SQL は同じパッケージの BookingMapper.xml に置き、H2 と PostgreSQL の
 * 共通の構文で書く（ADR-007）。
 */
@Mapper
public interface BookingMapper {

    void insertBooking(BookingRow row);

    void insertBookingVersion(BookingVersionRow row);

    void insertBookingSaga(BookingSagaRow row);

    /** 予約サガの状態を期待版で更新し、版を 1 進める（Bolt 25）。更新した行の数（0 なら楽観ロックの競合）。 */
    int updateBookingSaga(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("currentStep") String currentStep,
            @Param("updatedAt") OffsetDateTime updatedAt,
            @Param("expectedVersion") long expectedVersion);

    void insertProcessedCommand(ProcessedCommandRow row);

    Optional<BookingRow> findBookingByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    List<BookingVersionRow> findVersions(@Param("bookingId") UUID bookingId);

    boolean existsByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    Optional<String> findTrackingNumber(
            @Param("transportRequestNumber") String transportRequestNumber, @Param("quotationNo") int quotationNo);

    Optional<ProcessedCommandRow> findProcessedCommand(@Param("commandId") UUID commandId);

    Optional<BookingSagaRow> findSagaByBookingId(@Param("bookingId") UUID bookingId);

    /** 予約の要約を確定時刻（予約版 1）の新しい順、同じ時刻なら追跡番号の順に上限まで引く（S-10。Bolt 25b）。 */
    List<BookingSummaryRow> findRecentSummaries(@Param("limit") int limit);

    /** 荷主企業の予約の要約を S-10 と同じ並びで上限まで引く（C-06。他社の予約は引かない。BR-07。Bolt 27b）。索引 ix_booking_shipper_status。 */
    List<BookingSummaryRow> findRecentSummariesByShipper(
            @Param("shipperCompanyId") UUID shipperCompanyId, @Param("limit") int limit);

    /** 予約 ID の集合の予約サガの状態を 1 回で引く（S-10。Bolt 25b）。 */
    List<BookingSagaStatusRow> findSagaStatusesByBookingIds(@Param("bookingIds") Collection<UUID> bookingIds);
}
