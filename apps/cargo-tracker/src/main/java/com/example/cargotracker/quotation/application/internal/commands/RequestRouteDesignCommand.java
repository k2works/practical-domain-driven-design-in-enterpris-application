package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 荷主が見積りに詳細経路設計へ進むと回答するコマンド（US-24 AC1。Bolt 12）。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param shipperCompanyId 回答する荷主企業（自社の輸送要求だけを操作できる。Q-INV-08）
 * @param respondent 回答する荷主担当者（認証（US-18）までは仮の荷主の利用者）
 */
@Command
public record RequestRouteDesignCommand(
        TransportRequestNumber number, int quotationNo, CompanyId shipperCompanyId, UserId respondent) {

    public RequestRouteDesignCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(respondent, "respondent");
    }
}
