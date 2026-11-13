package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 照会記録。荷主・荷受人からの照会 1 件の経路と、有人対応へ移ったかの記録（IR-INV-02、KPI-02。Bolt 40-15）。引継ぎが不要でもカスタマーサポートが
 * 記録する。有人対応へ移ったか（{@link #escalated()}）は、引き継いだ有人案件の受付番号があるかで決まる（受付番号なしで「移った」とは言えない）。
 *
 * @param id 照会記録 ID
 * @param channel 経路
 * @param shipperCompanyId 荷主企業
 * @param trackingNumber 追跡番号
 * @param inquirerKind 照会者の種別
 * @param inquirerUserId 照会者の利用者 ID（Web の照会のとき）
 * @param receivedAt 受付時刻
 * @param caseNumber 引き継いだ有人案件の受付番号
 * @param recordedBy 記録した利用者（電話・メール）
 */
@AggregateRoot
public record InquiryRecord(
        UUID id,
        InquiryChannel channel,
        CompanyId shipperCompanyId,
        Optional<TrackingNumber> trackingNumber,
        InquirerKind inquirerKind,
        Optional<UserId> inquirerUserId,
        UtcInstant receivedAt,
        Optional<CaseNumber> caseNumber,
        Optional<UserId> recordedBy) {

    public InquiryRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(inquirerKind, "inquirerKind");
        Objects.requireNonNull(inquirerUserId, "inquirerUserId");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(caseNumber, "caseNumber");
        Objects.requireNonNull(recordedBy, "recordedBy");
    }

    /** 有人対応へ移ったか（引き継いだ有人案件の受付番号があるか）。 */
    public boolean escalated() {
        return caseNumber.isPresent();
    }
}
