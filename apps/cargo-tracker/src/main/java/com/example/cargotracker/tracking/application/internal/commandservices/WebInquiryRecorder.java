package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 顧客 Web の追跡照会を自動で照会記録にする（IR-INV-01。KPI-02 の分母。Bolt 42-1）。同じ利用者・同じ追跡番号の 30 分以内の再表示は 1 件に
 * まとめる（30 分ちょうどは別の照会）。有人対応へ移ったとみなす規則（照会の後 24 時間以内の有人案件の起票）は {@link WebInquiryEscalator}、荷主が Web で
 * 再開を依頼したときの記録は {@link #recordReopen}（W9 の Bolt 44。台帳 D-170。仮）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class WebInquiryRecorder {

    /** 再表示を 1 件にまとめる時間（IR-INV-01）。 */
    static final Duration MERGE_WINDOW = Duration.ofMinutes(30);

    private final InquiryRecordRepository inquiries;
    private final Clock clock;

    public WebInquiryRecorder(InquiryRecordRepository inquiries, Clock clock) {
        this.inquiries = inquiries;
        this.clock = clock;
    }

    /**
     * 荷主担当者が Web で問い合わせの再開を依頼したことを、顧客 Web の問い合わせ（WEB_INQUIRY）として記録する（KPI-02 の分母。有人対応へ
     * 移った照会。W9 の Bolt 44）。受付番号を持つので、有人対応へ移った記録になる。
     *
     * @param trackingNumber 問い合わせの追跡番号（分からなければ空）
     */
    @Transactional
    public void recordReopen(
            UserId user, CompanyId company, CaseNumber caseNumber, Optional<TrackingNumber> trackingNumber) {
        inquiries.save(new InquiryRecord(
                UUID.randomUUID(),
                InquiryChannel.WEB_INQUIRY,
                company,
                trackingNumber,
                InquirerKind.SHIPPER,
                Optional.of(user),
                new UtcInstant(clock.instant()),
                Optional.of(caseNumber),
                Optional.empty()));
    }

    /** 荷主担当者が自社の追跡記録を照会したことを記録する（30 分以内の再表示はまとめる）。 */
    @Transactional
    public void recordSelfLookup(UserId user, CompanyId company, TrackingNumber trackingNumber) {
        UtcInstant now = new UtcInstant(clock.instant());
        UtcInstant after = new UtcInstant(now.instant().minus(MERGE_WINDOW));
        if (inquiries.existsWebSelfAfter(user, trackingNumber, after)) {
            return;
        }
        inquiries.save(new InquiryRecord(
                UUID.randomUUID(),
                InquiryChannel.WEB_SELF,
                company,
                Optional.of(trackingNumber),
                InquirerKind.SHIPPER,
                Optional.of(user),
                now,
                Optional.empty(),
                Optional.empty()));
    }
}
