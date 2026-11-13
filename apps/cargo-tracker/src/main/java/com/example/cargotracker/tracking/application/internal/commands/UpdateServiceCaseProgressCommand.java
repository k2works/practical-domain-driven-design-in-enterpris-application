package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.util.Objects;

/**
 * 有人案件の進捗を更新するコマンド（US-20 AC4。Bolt 40-8）。
 *
 * @param number 受付番号
 * @param content 進捗の内容
 * @param nextUpdateAt 次回更新日時（指定しなければ {@code null} で今のまま）
 */
@Command
public record UpdateServiceCaseProgressCommand(CaseNumber number, String content, UtcInstant nextUpdateAt) {

    public UpdateServiceCaseProgressCommand {
        Objects.requireNonNull(number, "number");
    }
}
