package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 荷主が提示済みの見積りの条件を相談するコマンド（US-24 AC3。W9 の Bolt 46-1）。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param shipperCompanyId 相談する荷主企業（自社の輸送要求だけを操作できる。Q-INV-08）
 * @param requester 相談する荷主担当者
 * @param content 相談内容（前後の空白を除き 1〜500 文字。検証は応答のサービスが行う）
 */
@Command
public record ConsultQuotationCommand(
        TransportRequestNumber number, int quotationNo, CompanyId shipperCompanyId, UserId requester, String content) {

    public ConsultQuotationCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(content, "content");
    }
}
