package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import java.util.Objects;

/**
 * 有人案件を起票するコマンド（US-20 AC1・AC8。Bolt 40-7）。必須項目が足りなくても作れる入力を持つ（足りない項目は結果で返し、呼び手が入力を保持する）。
 *
 * @param draft 起票の入力
 */
@Command
public record OpenServiceCaseCommand(ServiceCaseDraft draft) {

    public OpenServiceCaseCommand {
        Objects.requireNonNull(draft, "draft");
    }
}
