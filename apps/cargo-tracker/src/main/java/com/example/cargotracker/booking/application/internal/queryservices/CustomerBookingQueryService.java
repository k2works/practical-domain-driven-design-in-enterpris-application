package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.shared.domain.CompanyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主の予約の照会（C-06 予約一覧。Bolt 27b）。ログインした荷主担当者の企業の予約だけを、リポジトリの照会で荷主企業を絞って返す
 * （BR-07。画面は企業を比べない）。追跡の荷主の照会（{@code CustomerTrackingQueryService}。Bolt 27）と同じ形。組み立ては
 * {@code BookingConfiguration} が担う。
 */
@Service
public class CustomerBookingQueryService {

    private final BookingRepository repository;
    private final BookingSagaRepository sagaRepository;

    public CustomerBookingQueryService(BookingRepository repository, BookingSagaRepository sagaRepository) {
        this.repository = repository;
        this.sagaRepository = sagaRepository;
    }

    /** 自社の予約の一覧。確定時刻の新しい順に上限まで返し、上限を超えたかを示す（上限より 1 件多く引いて判定する。S-10 と同じ）。 */
    @Transactional(readOnly = true)
    public RecentBookings recent(CompanyId shipperCompanyId) {
        return RecentBookings.of(
                limit -> repository.findRecentSummariesByShipper(shipperCompanyId, limit),
                sagaRepository::findStatusesByBookingIds);
    }
}
