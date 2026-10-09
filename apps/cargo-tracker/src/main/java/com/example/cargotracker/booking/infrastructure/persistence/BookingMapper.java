package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
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
}
