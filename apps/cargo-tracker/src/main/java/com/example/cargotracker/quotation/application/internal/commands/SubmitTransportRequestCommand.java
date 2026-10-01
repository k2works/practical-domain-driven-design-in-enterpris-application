package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;

/**
 * 輸送要求を提出するコマンド。Bolt 1 では出発地と目的地だけを受け取り、
 * 冪等性の commandId と期待版は後の Bolt で足す。
 *
 * @param shipperCompanyId 荷主企業
 * @param submittedBy 提出者
 * @param origin 出発地
 * @param destination 目的地
 */
public record SubmitTransportRequestCommand(
        CompanyId shipperCompanyId, UserId submittedBy, Location origin, Location destination) {}
