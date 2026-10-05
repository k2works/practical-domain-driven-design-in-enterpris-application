package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 見積りを社内承認して提示するコマンド（US-03 AC1）。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param approver 社内承認者（認証（US-18）までは仮の営業担当者）
 */
@Command
public record PresentQuotationCommand(TransportRequestNumber number, int quotationNo, UserId approver) {

    public PresentQuotationCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(approver, "approver");
    }
}
