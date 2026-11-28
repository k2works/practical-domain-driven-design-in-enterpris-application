package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 顧客 Web の照会の後 24 時間以内に、同じ荷主企業・同じ追跡番号（予約）の有人案件が起票されたら、その照会を有人対応へ移ったと更新する
 * （IR-INV-01。KPI-02 の「Web で完結した」から外す。W9 の Bolt 44。台帳 D-170。仮）。24 時間ちょうど前の照会は含む。荷主企業か、追跡番号（下書きの
 * 追跡番号、なければ予約から引く追跡記録）が分からない案件では何もしない。更新の対象は顧客 Web の自己照会（WEB_SELF）で、まだ移っていない記録だけ。
 * 起票と同じトランザクションの中で動く。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class WebInquiryEscalator implements InquiryEscalation {

    /** 有人対応へ移ったとみなす時間（IR-INV-01。仮）。 */
    static final Duration ESCALATION_WINDOW = Duration.ofHours(24);

    private final InquiryRecordRepository inquiries;
    private final TrackingRecordRepository trackingRecords;

    public WebInquiryEscalator(InquiryRecordRepository inquiries, TrackingRecordRepository trackingRecords) {
        this.inquiries = inquiries;
        this.trackingRecords = trackingRecords;
    }

    @Override
    public void onCaseOpened(ServiceCaseDraft draft, CaseNumber number, UtcInstant openedAt) {
        Optional<CompanyId> shipper = draft.shipperCompanyId();
        Optional<TrackingNumber> tracking = trackingNumberOf(draft);
        if (shipper.isEmpty() || tracking.isEmpty()) {
            return;
        }
        UtcInstant from = new UtcInstant(openedAt.instant().minus(ESCALATION_WINDOW));
        for (InquiryRecord lookup : inquiries.findUnescalatedWebSelf(shipper.get(), tracking.get(), from, openedAt)) {
            inquiries.escalate(lookup.id(), number);
        }
    }

    private Optional<TrackingNumber> trackingNumberOf(ServiceCaseDraft draft) {
        if (draft.trackingNumber().isPresent()) {
            return draft.trackingNumber();
        }
        return draft.bookingId().flatMap(trackingRecords::findByBookingId).map(TrackingRecord::trackingNumber);
    }
}
