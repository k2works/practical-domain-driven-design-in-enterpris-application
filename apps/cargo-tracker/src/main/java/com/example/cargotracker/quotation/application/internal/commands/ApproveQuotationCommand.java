package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 荷主が見積りと割り当てた経路を承認するコマンド（US-24 AC4・AC5。Bolt 20）。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param shipperCompanyId 承認する荷主企業（自社の輸送要求だけを操作できる。Q-INV-08）
 * @param approver 承認する荷主担当者（ログインした利用者。BR-01）
 */
@Command
public record ApproveQuotationCommand(
        TransportRequestNumber number, int quotationNo, CompanyId shipperCompanyId, UserId approver) {

    public ApproveQuotationCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(approver, "approver");
    }
}
