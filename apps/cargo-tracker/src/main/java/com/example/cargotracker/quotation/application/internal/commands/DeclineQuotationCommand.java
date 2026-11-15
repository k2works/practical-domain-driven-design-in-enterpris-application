package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 荷主が提示済みの見積りを理由を付けて辞退するコマンド（US-24 AC2。W9 の Bolt 46）。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param shipperCompanyId 辞退する荷主企業（自社の輸送要求だけを操作できる。Q-INV-08）
 * @param respondent 辞退する荷主担当者
 * @param reason 辞退の理由（前後の空白を除き 1〜500 文字。検証は応答のサービスが行う）
 */
@Command
public record DeclineQuotationCommand(
        TransportRequestNumber number, int quotationNo, CompanyId shipperCompanyId, UserId respondent, String reason) {

    public DeclineQuotationCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(respondent, "respondent");
        Objects.requireNonNull(reason, "reason");
    }
}
