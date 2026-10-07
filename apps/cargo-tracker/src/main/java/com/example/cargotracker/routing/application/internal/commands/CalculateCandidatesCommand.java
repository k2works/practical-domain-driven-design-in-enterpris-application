package com.example.cargotracker.routing.application.internal.commands;

import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 経路設計者が経路候補を算出・再算出するコマンド（US-06 AC1。Bolt 17）。
 *
 * @param number 案件番号
 * @param operator 操作者（ログインした経路設計者。案件の最終更新者に残す。Bolt 19）
 */
@Command
public record CalculateCandidatesCommand(RoutingCaseNumber number, UserId operator) {

    public CalculateCandidatesCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(operator, "operator");
    }
}
