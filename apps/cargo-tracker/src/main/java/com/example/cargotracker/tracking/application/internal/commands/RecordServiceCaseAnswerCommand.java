package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Objects;

/**
 * 有人案件の回答を記録するコマンド（US-20 AC5。Bolt 40-8）。
 *
 * @param number 受付番号
 * @param content 回答の内容
 */
@Command
public record RecordServiceCaseAnswerCommand(CaseNumber number, String content) {

    public RecordServiceCaseAnswerCommand {
        Objects.requireNonNull(number, "number");
    }
}
