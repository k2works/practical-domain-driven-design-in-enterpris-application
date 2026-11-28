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
 * 本予約の前に、輸送要求の条件を変更するコマンド（US-05 AC1。Bolt 62）。前の版の書類は引き継ぎ、書類を足せる（Q-INV-16）。
 *
 * @param number 業務番号
 * @param shipperCompanyId 荷主企業（自社の輸送要求だけを変更できる。照会を荷主企業で絞る。Q-INV-08）
 * @param submittedBy 提出者
 * @param baseVersionNo 荷主が見ていた版番号。現在の版でなければ受け付けない
 * @param terms 輸送条件の入力（提出と同じ検証を通す）
 * @param attachments 足す書類（前の版の書類は引き継ぐ）
 */
@Command
public record ReviseTransportRequestCommand(
        TransportRequestNumber number,
        CompanyId shipperCompanyId,
        UserId submittedBy,
        int baseVersionNo,
        ShipmentTermsInput terms,
        List<RequiredDocumentAttachment> attachments) {

    public ReviseTransportRequestCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(terms, "terms");
        attachments = List.copyOf(attachments);
    }
}
