package com.example.cargotracker.booking.application.sagas;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.annotation.ddd.Saga;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約サガ。本予約の確定から追跡の開始までの状態（処理中・完了・失敗・有人確認要）を予約の 1 か所に持つ（ADR-015）。
 * 本予約の確定と同じトランザクションで処理中として始める。後続の追跡の開始は追跡が DE-07 を購読して行い、結果を予約の公開 API で
 * 返す（Bolt 25）。処理中の滞留を有人確認要にするのは W8。
 */
@Saga
public final class BookingSaga {

    private static final long INITIAL_VERSION = 0;

    private final UUID id;
    private final BookingId bookingId;
    private final TrackingNumber trackingNumber;
    private final BookingSagaStatus status;
    private final BookingSagaStep currentStep;
    private final UtcInstant startedAt;
    private final long version;

    private BookingSaga(
            UUID id,
            BookingId bookingId,
            TrackingNumber trackingNumber,
            BookingSagaStatus status,
            BookingSagaStep currentStep,
            UtcInstant startedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.status = Objects.requireNonNull(status, "status");
        this.currentStep = Objects.requireNonNull(currentStep, "currentStep");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.version = version;
    }

    /** 本予約の確定とともに、追跡の開始を待つ処理中として始める。 */
    public static BookingSaga start(BookingId bookingId, TrackingNumber trackingNumber, UtcInstant startedAt) {
        return new BookingSaga(
                UUID.randomUUID(),
                bookingId,
                trackingNumber,
                BookingSagaStatus.IN_PROGRESS,
                BookingSagaStep.START_TRACKING,
                startedAt,
                INITIAL_VERSION);
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    public static BookingSaga reconstitute(
            UUID id,
            BookingId bookingId,
            TrackingNumber trackingNumber,
            BookingSagaStatus status,
            BookingSagaStep currentStep,
            UtcInstant startedAt,
            long version) {
        return new BookingSaga(id, bookingId, trackingNumber, status, currentStep, startedAt, version);
    }

    public UUID id() {
        return id;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public BookingSagaStatus status() {
        return status;
    }

    public BookingSagaStep currentStep() {
        return currentStep;
    }

    public UtcInstant startedAt() {
        return startedAt;
    }

    public long version() {
        return version;
    }

    /** 後続が完了したか。処理中を完了と表示しないために画面が使う。 */
    public boolean isCompleted() {
        return status == BookingSagaStatus.COMPLETED;
    }
}
