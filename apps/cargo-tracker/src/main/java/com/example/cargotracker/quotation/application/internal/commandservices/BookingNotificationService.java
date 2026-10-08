package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.api.BookingNotification;
import com.example.cargotracker.quotation.api.BookingNotificationReceipt;
import com.example.cargotracker.quotation.api.BookingNotificationRequest;
import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積りの公開 API の予約確定済みの通知の実装（ADR-014、DE-07。Bolt 23）。予約の DE-07 の listener のトランザクションの中で、
 * 輸送要求を予約確定済みにする。輸送要求を進める処理は DE-03・DE-16・DE-21・DE-04 の listener と共通の部品を使い、楽観ロックの
 * 競合（DE-04 の listener と並行したとき）は読み直してやり直す（Bolt 22 の割り込み）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class BookingNotificationService implements BookingNotification {

    private static final Logger LOG = LoggerFactory.getLogger(BookingNotificationService.class);

    private final TransportRequestProgression progression;

    public BookingNotificationService(TransportRequestRepository repository) {
        this.progression = new TransportRequestProgression(
                repository, LOG, "DE-07", TransportRequestStatus.BOOKED, "予約確定済み", TransportRequest::markBooked);
    }

    @Override
    @Transactional
    public BookingNotificationReceipt notifyBooked(BookingNotificationRequest request) {
        return switch (progression.advance(
                request.transportRequestId(), request.quotationId(), request.transportRequestVersionNo())) {
            case ADVANCED -> new BookingNotificationReceipt.Booked();
            case ALREADY_REACHED -> new BookingNotificationReceipt.AlreadyBooked();
            case NOT_ADVANCED ->
                new BookingNotificationReceipt.NotBooked(BookingNotificationReceipt.NotBooked.NOT_READY_TO_BOOK);
            case NOT_FOUND ->
                new BookingNotificationReceipt.NotBooked(
                        BookingNotificationReceipt.NotBooked.TRANSPORT_REQUEST_NOT_FOUND);
        };
    }
}
