package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.entities.BookingVersion;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物予約のリポジトリの MyBatis 実装（Bolt 23）。貨物予約と予約版（追記専用）の表を組み立てて集約にする。
 * 業務番号・荷主企業は予約版 1 の予約条件から貨物予約の表に写す（業務番号から予約をたどるため。R-31）。
 */
@Repository
public class MyBatisBookingRepository implements BookingRepository {

    private final BookingMapper mapper;
    private final Clock clock;

    public MyBatisBookingRepository(BookingMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 同時の確定の決着を示す一意制約の名前。1 つの見積りから予約は 1 件（B-INV-11。見積り ID、業務番号と見積り番号）と、同じコマンド ID の
     * 確定は 1 回（B-INV-03。処理済みコマンドの主キー。同じコマンド ID で別の見積りを同時に送ったとき。Bolt 24 レビュー P-1）。
     * DB によって大文字・小文字が変わるため、区別せずに探す。
     */
    private static final List<String> SETTLED_UNIQUE_KEYS =
            List.of("uk_booking_quotation", "uk_booking_transport_request_quotation", "pk_processed_command");

    private static final String CONFIRM_BOOKING = "ConfirmBooking";
    private static final String RESULT_REF_SEPARATOR = ":";

    /**
     * 確定した貨物予約を予約版と処理済みコマンドとあわせて追加する。同じ見積りの UK（見積り ID、業務番号と見積り番号）に違反したら、
     * セーブポイントに戻してドメインの例外にする（PostgreSQL は制約違反でトランザクションを中断するため。経路設計案件の保存と同じ）。
     * 呼び出し側は同じトランザクションで勝った側の結果を読み直せる（Bolt 24）。追跡番号とコマンド ID の重なりは発行で避けており
     * （{@code RandomTrackingNumberIssuer}、画面が開くたびに発行する UUID）、それでも重なったら技術の失敗として返す（Bolt 23 レビュー M-1）。
     */
    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void save(Booking booking, UUID operator, ProcessedCommand processedCommand) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            mapper.insertBooking(new BookingRow(
                    booking.id().value(),
                    booking.trackingNumber().value(),
                    booking.transportRequestNumber(),
                    booking.quotationNo(),
                    booking.currentVersion().terms().quotationId(),
                    booking.shipperCompanyId().value(),
                    booking.status().name(),
                    booking.transportPhase().name(),
                    booking.currentVersion().versionNo(),
                    booking.aggregateVersion(),
                    now,
                    operator,
                    now,
                    operator));
            for (BookingVersion version : booking.versions()) {
                mapper.insertBookingVersion(toRow(booking.id(), version));
            }
            mapper.insertProcessedCommand(toRow(processedCommand));
        } catch (DuplicateKeyException e) {
            if (SETTLED_UNIQUE_KEYS.stream().anyMatch(key -> violates(e, key))) {
                throw new DuplicateBookingException(
                        "同時の確定がすでに決着している（同じ見積りの予約か、同じコマンド ID の確定）: " + processedCommand.commandId(), e);
            }
            throw new IllegalStateException("貨物予約の一意制約に違反した（追跡番号の重なりなど）: " + booking.id(), e);
        }
    }

    private static boolean violates(DuplicateKeyException e, String constraint) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraint);
    }

    @Override
    public List<BookingSummary> findRecentSummaries(int limit) {
        return mapper.findRecentSummaries(limit).stream()
                .map(row -> new BookingSummary(
                        new BookingId(row.id()),
                        new TrackingNumber(row.trackingNumber()),
                        row.transportRequestNumber(),
                        row.quotationNo(),
                        new UtcInstant(row.committedAt().toInstant())))
                .toList();
    }

    @Override
    public Optional<Booking> findByTrackingNumber(TrackingNumber trackingNumber) {
        return mapper.findBookingByTrackingNumber(trackingNumber.value()).map(this::toBooking);
    }

    @Override
    public boolean existsByTrackingNumber(TrackingNumber trackingNumber) {
        return mapper.existsByTrackingNumber(trackingNumber.value());
    }

    @Override
    public Optional<ProcessedCommand> findProcessedCommand(CommandId commandId) {
        return mapper.findProcessedCommand(commandId.value()).map(MyBatisBookingRepository::toProcessedCommand);
    }

    @Override
    public Optional<TrackingNumber> findTrackingNumber(String transportRequestNumber, int quotationNo) {
        return mapper.findTrackingNumber(transportRequestNumber, quotationNo).map(TrackingNumber::new);
    }

    private static ProcessedCommandRow toRow(ProcessedCommand processed) {
        return new ProcessedCommandRow(
                processed.commandId().value(),
                CONFIRM_BOOKING,
                processed.payloadHash(),
                processed.bookingId().value()
                        + RESULT_REF_SEPARATOR
                        + processed.trackingNumber().value(),
                processed.processedAt().instant().atOffset(ZoneOffset.UTC));
    }

    private static ProcessedCommand toProcessedCommand(ProcessedCommandRow row) {
        String[] result =
                row.resultRef() == null ? new String[0] : row.resultRef().split(RESULT_REF_SEPARATOR, 2);
        if (result.length != 2) {
            throw new IllegalStateException("処理済みコマンドの結果の参照が「予約 ID:追跡番号」の形でない: commandId=" + row.commandId());
        }
        return new ProcessedCommand(
                new CommandId(row.commandId()),
                row.payloadHash(),
                new BookingId(UUID.fromString(result[0])),
                new TrackingNumber(result[1]),
                new UtcInstant(row.processedAt().toInstant()));
    }

    private Booking toBooking(BookingRow row) {
        List<BookingVersion> versions = mapper.findVersions(row.id()).stream()
                .map(version -> toVersion(row, version))
                .toList();
        return Booking.reconstitute(
                new BookingId(row.id()),
                new TrackingNumber(row.trackingNumber()),
                BookingStatus.valueOf(row.status()),
                TransportPhase.valueOf(row.transportPhase()),
                versions,
                row.version());
    }

    private static BookingVersion toVersion(BookingRow booking, BookingVersionRow row) {
        return new BookingVersion(
                row.bookingVersionNo(),
                new BookingTerms(
                        row.transportRequestId(),
                        row.transportRequestVersionNo(),
                        booking.transportRequestNumber(),
                        row.quotationId(),
                        booking.quotationNo(),
                        new CompanyId(booking.shipperCompanyId()),
                        new CompanyId(row.consigneeCompanyId()),
                        row.routingCaseNumber(),
                        row.routeVersionNo(),
                        row.cargoCategory(),
                        row.cargoSummary(),
                        row.shipperApproverId()),
                row.confirmedBy(),
                new UtcInstant(row.committedAt().toInstant()));
    }

    private static BookingVersionRow toRow(BookingId bookingId, BookingVersion version) {
        BookingTerms terms = version.terms();
        return new BookingVersionRow(
                bookingId.value(),
                version.versionNo(),
                terms.transportRequestId(),
                terms.transportRequestVersionNo(),
                terms.quotationId(),
                terms.routingCaseNumber(),
                terms.routeVersionNo(),
                terms.consigneeCompanyId().value(),
                terms.cargoCategory(),
                terms.cargoSummary(),
                terms.shipperApproverId(),
                version.confirmedBy(),
                version.committedAt().instant().atOffset(ZoneOffset.UTC));
    }
}
