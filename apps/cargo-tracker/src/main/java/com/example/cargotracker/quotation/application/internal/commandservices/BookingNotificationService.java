package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予約確定済みの通知の入力ポート（ADR-014、DE-07。Bolt 23）。見積りの公開 API のアダプター（{@code interfaces.api.internal}）が委ねる。予約の DE-07 の listener のトランザクションの中で、
 * 輸送要求を予約確定済みにする。輸送要求を進める処理は DE-03・DE-16・DE-21・DE-04 の listener と共通の部品を使い、楽観ロックの
 * 競合（DE-04 の listener と並行したとき）は読み直してやり直す（Bolt 22 の割り込み）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class BookingNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(BookingNotificationService.class);

    private final TransportRequestProgression progression;

    public BookingNotificationService(TransportRequestRepository repository) {
        this.progression = new TransportRequestProgression(
                repository, LOG, "DE-07", TransportRequestStatus.BOOKED, "予約確定済み", TransportRequest::markBooked);
    }

    /**
     * 輸送要求を予約確定済みにする。
     *
     * @param transportRequestId 輸送要求 ID
     * @param quotationId 予約に使った見積り ID
     * @param transportRequestVersionNo 見積りの対象の輸送要求の版番号
     * @return 進めた結果
     */
    @Transactional
    public TransportRequestProgression.Result notifyBooked(
            UUID transportRequestId, UUID quotationId, int transportRequestVersionNo) {
        return progression.advance(transportRequestId, quotationId, transportRequestVersionNo);
    }
}
