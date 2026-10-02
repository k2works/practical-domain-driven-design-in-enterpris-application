package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 輸送要求を提出するコマンド。冪等性の commandId と期待版は後の Bolt で足す。
 *
 * @param shipperCompanyId 荷主企業
 * @param submittedBy 提出者
 * @param terms 輸送条件の入力（欠けた項目があってよい。検証はコマンドサービスが行う）
 */
public record SubmitTransportRequestCommand(CompanyId shipperCompanyId, UserId submittedBy, ShipmentTermsInput terms) {

    public SubmitTransportRequestCommand {
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(terms, "terms");
    }
}
