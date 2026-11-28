package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Objects;

/**
 * 有人案件を再開するコマンド（US-20 AC6、US-23 AC2。顧客の未解決の回答を最後の回答に記録する。Bolt 40-8）。
 *
 * @param number 受付番号
 * @param customerResponse 顧客の回答
 */
@Command
public record ReopenServiceCaseCommand(CaseNumber number, String customerResponse) {

    public ReopenServiceCaseCommand {
        Objects.requireNonNull(number, "number");
    }
}
