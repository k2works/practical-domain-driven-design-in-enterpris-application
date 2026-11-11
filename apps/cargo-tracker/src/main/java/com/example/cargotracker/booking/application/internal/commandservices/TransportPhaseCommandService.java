package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.TransportPhaseChange;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhaseRefusal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集荷・引渡しの通知で予約の状態と輸送段階を進める入力ポート（ADR-014。Bolt 39g2）。予約の公開 API（{@code booking.interfaces.api}）の実装が委ねる。
 * 予約の行を押さえてから読み直し（同じ予約への集荷と引渡しの通知を直列にする）、進めたときだけ保存する。記録済み・記録しないは書かず、版を上げない。
 * 予約のない通知は、再配信で直らない欠けなので、警告のログを残して「見つからない」を返す（例外にしない。前例 {@link BookingSagaCommandService}）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class TransportPhaseCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(TransportPhaseCommandService.class);

    private final BookingRepository repository;

    public TransportPhaseCommandService(BookingRepository repository) {
        this.repository = repository;
    }

    /**
     * 集荷実績の採用を記録する（DE-09 の通知）。
     *
     * @param bookingId 予約 ID
     * @param operator 集荷を登録・採用した追跡管理者（予約の更新者に書く）
     * @param trackingNumber 追跡番号の表記（ログに使う）
     * @param milestoneNo 実績番号（ログに使う）
     * @return 予約の結果
     */
    @Transactional
    public TransportPhaseOutcome recordPickup(
            BookingId bookingId, UUID operator, String trackingNumber, int milestoneNo) {
        return record(bookingId, operator, trackingNumber, milestoneNo, Booking::recordPickup);
    }

    /**
     * 引渡しを記録する（DE-10 の通知）。
     *
     * @param bookingId 予約 ID
     * @param operator 引渡しを登録・採用した追跡管理者（予約の更新者に書く）
     * @param trackingNumber 追跡番号の表記（ログに使う）
     * @param milestoneNo 実績番号（ログに使う）
     * @return 予約の結果
     */
    @Transactional
    public TransportPhaseOutcome recordDelivery(
            BookingId bookingId, UUID operator, String trackingNumber, int milestoneNo) {
        return record(bookingId, operator, trackingNumber, milestoneNo, Booking::recordDelivery);
    }

    private TransportPhaseOutcome record(
            BookingId bookingId,
            UUID operator,
            String trackingNumber,
            int milestoneNo,
            Function<Booking, TransportPhaseChange> recording) {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(operator, "operator");
        Optional<Booking> found = repository.findByIdForUpdate(bookingId);
        if (found.isEmpty()) {
            LOG.warn("集荷・引渡しの通知を受けたが、予約がない: 予約 {}、追跡番号 {}、実績番号 {}", bookingId.value(), trackingNumber, milestoneNo);
            return TransportPhaseOutcome.BOOKING_NOT_FOUND;
        }
        return switch (recording.apply(found.get())) {
            case TransportPhaseChange.Changed(Booking changed) -> {
                repository.update(changed, operator);
                yield TransportPhaseOutcome.RECORDED;
            }
            case TransportPhaseChange.Unchanged _ -> TransportPhaseOutcome.ALREADY_RECORDED;
            case TransportPhaseChange.Refused(TransportPhaseRefusal reason) -> refused(reason);
        };
    }

    private static TransportPhaseOutcome refused(TransportPhaseRefusal reason) {
        return switch (reason) {
            case BOOKING_CANCELLED -> TransportPhaseOutcome.BOOKING_CANCELLED;
            case BOOKING_UNDER_REQUEST -> TransportPhaseOutcome.BOOKING_UNDER_REQUEST;
        };
    }
}
