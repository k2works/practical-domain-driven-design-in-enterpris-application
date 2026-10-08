package com.example.cargotracker.booking.acceptance;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * メモリ上の貨物予約のリポジトリ（業務ルール層の受入シナリオ）。追跡番号と見積り ID の一意を表の制約と同じく守る（B-INV-11）。
 * 貨物予約は予約版を含めて不変なので、保存したインスタンスをそのまま返しても呼び出し側が書き換えられない（T-61）。
 */
public class InMemoryBookingRepository implements BookingRepository {

    private final Map<String, Booking> byTrackingNumber = new LinkedHashMap<>();

    @Override
    public synchronized void save(Booking booking, UUID operator) {
        UUID quotationId = booking.currentVersion().terms().quotationId();
        boolean sameQuotation = byTrackingNumber.values().stream()
                .anyMatch(saved -> saved.currentVersion().terms().quotationId().equals(quotationId));
        if (sameQuotation) {
            throw new DuplicateBookingException("同じ見積りの予約がすでにある: " + quotationId, null);
        }
        if (byTrackingNumber.containsKey(booking.trackingNumber().value())) {
            throw new IllegalStateException(
                    "追跡番号が重なった: " + booking.trackingNumber().value());
        }
        byTrackingNumber.put(booking.trackingNumber().value(), booking);
    }

    @Override
    public synchronized Optional<Booking> findByTrackingNumber(TrackingNumber trackingNumber) {
        return Optional.ofNullable(byTrackingNumber.get(trackingNumber.value()));
    }

    @Override
    public synchronized boolean existsByTrackingNumber(TrackingNumber trackingNumber) {
        return byTrackingNumber.containsKey(trackingNumber.value());
    }

    @Override
    public synchronized boolean existsByQuotationId(UUID quotationId) {
        return byTrackingNumber.values().stream()
                .anyMatch(saved -> saved.currentVersion().terms().quotationId().equals(quotationId));
    }

    public synchronized List<Booking> findAll() {
        return new ArrayList<>(byTrackingNumber.values());
    }

    public synchronized void clear() {
        byTrackingNumber.clear();
    }
}
