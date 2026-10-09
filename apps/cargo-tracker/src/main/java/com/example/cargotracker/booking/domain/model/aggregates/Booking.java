package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.booking.domain.model.entities.BookingVersion;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingConditions;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.annotation.ddd.CoreConcept;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 貨物予約。本予約で確定した輸送の契約で、予約版と追跡番号を持つ（集約ルート。US-04、BR-01。Bolt 23）。
 * 予約版は不変で（B-INV-08）、変更は新しい予約版になる（US-05）。確定の 5 条件がそろったときだけ作る（B-INV-01）。
 * 見積りの有効性（commit 時刻での失効）は見積りが判定し、その結果を確定条件の「有効な見積り」として受け取る（ADR-016）。
 */
@AggregateRoot
@CoreConcept
public final class Booking {

    private static final long INITIAL_AGGREGATE_VERSION = 0;
    private static final int FIRST_VERSION_NO = 1;

    private final BookingId id;
    private final TrackingNumber trackingNumber;
    private final BookingStatus status;
    private final TransportPhase transportPhase;
    private final List<BookingVersion> versions;
    private final long aggregateVersion;

    private Booking(
            BookingId id,
            TrackingNumber trackingNumber,
            BookingStatus status,
            TransportPhase transportPhase,
            List<BookingVersion> versions,
            long aggregateVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.status = Objects.requireNonNull(status, "status");
        this.transportPhase = Objects.requireNonNull(transportPhase, "transportPhase");
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("予約版が 1 つ以上必要です");
        }
        this.versions = versions.stream()
                .sorted(Comparator.comparingInt(BookingVersion::versionNo))
                .toList();
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 本予約を確定する（US-04 AC1、B-INV-01）。5 条件がそろえば、予約版 1・追跡番号・commit 時刻・確定者を持つ貨物予約を作り、
     * DE-07 を返す。1 つでも欠ければ確定せず、欠けた条件を示す。
     *
     * @param id 新しい予約 ID
     * @param conditions 確定条件
     * @param terms 予約条件（見積りの写し）
     * @param trackingNumber 発行した追跡番号
     * @param confirmedBy 確定した営業担当者の利用者 ID
     * @param committedAt commit 時刻（ADR-016）
     * @return 確定した貨物予約と DE-07
     * @throws BookingConfirmationRejected 確定条件が欠けているとき
     */
    public static BookingConfirmation confirm(
            BookingId id,
            BookingConditions conditions,
            BookingTerms terms,
            TrackingNumber trackingNumber,
            UUID confirmedBy,
            UtcInstant committedAt) {
        Objects.requireNonNull(conditions, "conditions");
        List<BookingCondition> missing = conditions.missing();
        if (!missing.isEmpty()) {
            throw new BookingConfirmationRejected(missing);
        }
        BookingVersion version = new BookingVersion(FIRST_VERSION_NO, terms, confirmedBy, committedAt);
        Booking booking = new Booking(
                id,
                trackingNumber,
                BookingStatus.CONFIRMED,
                TransportPhase.BEFORE_PICKUP,
                List.of(version),
                INITIAL_AGGREGATE_VERSION);
        return new BookingConfirmation(
                booking,
                new BookingConfirmed(
                        id.value(),
                        version.versionNo(),
                        trackingNumber.value(),
                        terms.quotationId(),
                        terms.transportRequestId(),
                        terms.transportRequestVersionNo(),
                        terms.transportRequestNumber(),
                        terms.routingCaseNumber(),
                        terms.routeVersionNo(),
                        committedAt,
                        INITIAL_AGGREGATE_VERSION));
    }

    /**
     * 保存されている状態から貨物予約を組み立てる（リポジトリが使う）。
     */
    public static Booking reconstitute(
            BookingId id,
            TrackingNumber trackingNumber,
            BookingStatus status,
            TransportPhase transportPhase,
            List<BookingVersion> versions,
            long aggregateVersion) {
        return new Booking(id, trackingNumber, status, transportPhase, List.copyOf(versions), aggregateVersion);
    }

    public BookingId id() {
        return id;
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public BookingStatus status() {
        return status;
    }

    public TransportPhase transportPhase() {
        return transportPhase;
    }

    /** 予約版（予約版番号の順）。 */
    public List<BookingVersion> versions() {
        return List.copyOf(versions);
    }

    /** 現在の予約版（予約版番号がいちばん大きいもの）。 */
    public BookingVersion currentVersion() {
        return versions.getLast();
    }

    /** 業務番号の表記（予約版が変わっても変わらない。R-31）。 */
    public String transportRequestNumber() {
        return versions.getFirst().terms().transportRequestNumber();
    }

    /** 見積り番号（業務番号と見積り番号で同じ見積りの予約を引く。Bolt 24）。 */
    public int quotationNo() {
        return versions.getFirst().terms().quotationNo();
    }

    /** 荷主企業 ID。 */
    public CompanyId shipperCompanyId() {
        return versions.getFirst().terms().shipperCompanyId();
    }

    /** 集約の版（楽観ロック）。 */
    public long aggregateVersion() {
        return aggregateVersion;
    }
}
