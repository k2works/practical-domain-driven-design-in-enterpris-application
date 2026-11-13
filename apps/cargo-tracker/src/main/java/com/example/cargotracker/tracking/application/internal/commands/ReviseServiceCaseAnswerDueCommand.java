package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Objects;

/**
 * 有人案件の回答期限を後ろへ変更するコマンド（UC-20。期限を超える場合。理由は必須。Bolt 40-8）。
 *
 * @param number 受付番号
 * @param reason 理由
 * @param newDue 新しい回答期限
 */
@Command
public record ReviseServiceCaseAnswerDueCommand(CaseNumber number, String reason, UtcInstant newDue) {

    public ReviseServiceCaseAnswerDueCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(newDue, "newDue");
    }
}
