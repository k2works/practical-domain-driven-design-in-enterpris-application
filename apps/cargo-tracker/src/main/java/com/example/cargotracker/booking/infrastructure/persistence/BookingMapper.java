package com.example.cargotracker.booking.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 貨物予約・予約版・予約サガの表の MyBatis マッパー。SQL は同じパッケージの BookingMapper.xml に置き、H2 と PostgreSQL の
 * 共通の構文で書く（ADR-007）。
 */
@Mapper
public interface BookingMapper {

    void insertBooking(BookingRow row);

    void insertBookingVersion(BookingVersionRow row);

    void insertBookingSaga(BookingSagaRow row);

    Optional<BookingRow> findBookingByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    List<BookingVersionRow> findVersions(@Param("bookingId") UUID bookingId);

    boolean existsByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    boolean existsByQuotationId(@Param("quotationId") UUID quotationId);

    Optional<BookingSagaRow> findSagaByBookingId(@Param("bookingId") UUID bookingId);
}
