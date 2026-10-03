package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * 輸送要求を提出するコマンド。冪等性の commandId と期待版は後の Bolt で足す。
 *
 * @param shipperCompanyId 荷主企業
 * @param submittedBy 提出者
 * @param terms 輸送条件の入力（欠けた項目があってよい。検証はコマンドサービスが行う）
 * @param attachments 添付した書類（任意。D-20）
 */
public record SubmitTransportRequestCommand(
        CompanyId shipperCompanyId,
        UserId submittedBy,
        ShipmentTermsInput terms,
        List<RequiredDocumentAttachment> attachments) {

    public SubmitTransportRequestCommand {
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(terms, "terms");
        attachments = List.copyOf(attachments);
    }

    /** 書類を添付しない提出。 */
    public SubmitTransportRequestCommand(CompanyId shipperCompanyId, UserId submittedBy, ShipmentTermsInput terms) {
        this(shipperCompanyId, submittedBy, terms, List.of());
    }
}
