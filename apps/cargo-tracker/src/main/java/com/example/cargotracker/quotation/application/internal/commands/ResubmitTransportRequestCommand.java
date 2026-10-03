package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * 差し戻された輸送要求を、直した輸送条件で再提出するコマンド（Q-INV-15）。前の版の書類は引き継ぎ、書類を足せる（Q-INV-16）。
 *
 * @param number 業務番号
 * @param shipperCompanyId 荷主企業（自社の輸送要求だけを再提出できる。照会を荷主企業で絞る）
 * @param submittedBy 提出者
 * @param terms 輸送条件の入力（提出と同じ検証を通す）
 * @param attachments 足す書類（前の版の書類は引き継ぐ）
 */
@Command
public record ResubmitTransportRequestCommand(
        TransportRequestNumber number,
        CompanyId shipperCompanyId,
        UserId submittedBy,
        ShipmentTermsInput terms,
        List<RequiredDocumentAttachment> attachments) {

    public ResubmitTransportRequestCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(terms, "terms");
        attachments = List.copyOf(attachments);
    }

    /** 書類を足さない再提出。 */
    public ResubmitTransportRequestCommand(
            TransportRequestNumber number, CompanyId shipperCompanyId, UserId submittedBy, ShipmentTermsInput terms) {
        this(number, shipperCompanyId, submittedBy, terms, List.of());
    }
}
