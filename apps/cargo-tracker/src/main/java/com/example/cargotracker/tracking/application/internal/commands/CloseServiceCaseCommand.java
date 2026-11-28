package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Objects;

/**
 * 有人案件を終結するコマンド（US-20 AC5・AC7。復旧照合が必要で未完了なら終結しない。Bolt 40-8）。
 *
 * @param number 受付番号
 */
@Command
public record CloseServiceCaseCommand(CaseNumber number) {

    public CloseServiceCaseCommand {
        Objects.requireNonNull(number, "number");
    }
}
