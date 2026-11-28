package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡の開始の結果で予約サガを進める入力ポート（ADR-015。Bolt 25）。予約の公開 API（{@code booking.interfaces.api}）の実装が委ねる。
 * 予約 ID で冪等で、処理中の予約サガだけを完了にし、完了済みには何もしない。予約サガのない予約は、再配信で直らない欠けなので、
 * 警告のログを残して「見つからない」を返す（例外にしない。T-62）。同時の通知の負けた側は楽観ロックの競合の例外のまま戻り、再配信に任せる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class BookingSagaCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(BookingSagaCommandService.class);

    private final BookingSagaRepository sagaRepository;

    public BookingSagaCommandService(BookingSagaRepository sagaRepository) {
        this.sagaRepository = sagaRepository;
    }

    /**
     * 追跡を開始したことを受け、処理中の予約サガを完了にする。
     *
     * @param bookingId 予約 ID
     * @param startedAt 追跡を開始した時刻（ログに残す）
     * @return 予約サガの結果
     */
    @Transactional
    public TrackingStartOutcome completeTrackingStart(BookingId bookingId, UtcInstant startedAt) {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(startedAt, "startedAt");
        Optional<BookingSaga> found = sagaRepository.findByBookingId(bookingId);
        if (found.isEmpty()) {
            LOG.warn("追跡の開始の結果を受けたが、予約サガがない: 予約 {}、開始時刻 {}", bookingId.value(), startedAt.instant());
            return TrackingStartOutcome.SAGA_NOT_FOUND;
        }
        BookingSaga saga = found.get();
        if (saga.isCompleted()) {
            return TrackingStartOutcome.ALREADY_COMPLETED;
        }
        if (saga.status() != BookingSagaStatus.IN_PROGRESS) {
            // 失敗・有人確認要（W8）に遅れて届いた結果。状態を変えず、再配信で直らないので例外にしない（Bolt 25 レビュー A-1。T-62）
            LOG.warn("追跡の開始の結果を受けたが、予約サガが処理中でないため完了にしなかった: 予約 {}、状態 {}", bookingId.value(), saga.status());
            return TrackingStartOutcome.SAGA_NOT_IN_PROGRESS;
        }
        sagaRepository.update(saga.complete());
        return TrackingStartOutcome.COMPLETED;
    }
}
