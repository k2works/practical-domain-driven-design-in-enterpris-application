package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Objects;

/**
 * 有人案件を受領するコマンド（US-20 AC2。Bolt 40-7）。担当者は操作者、受領時刻は時計から得る。
 *
 * @param number 受付番号
 */
@Command
public record ReceiveServiceCaseCommand(CaseNumber number) {

    public ReceiveServiceCaseCommand {
        Objects.requireNonNull(number, "number");
    }
}
