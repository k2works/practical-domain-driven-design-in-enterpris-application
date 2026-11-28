package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;

/**
 * 見積りを作って算出するコマンド（US-03 AC1・AC3）。作成中のまま保存する操作は入れない（2026-10-05 の決定）。
 *
 * @param number 業務番号
 * @param input 見積りの入力（料金明細・通貨・有効期限・経路方針）
 */
@Command
public record CalculateQuotationCommand(TransportRequestNumber number, QuotationInput input) {

    public CalculateQuotationCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(input, "input");
    }
}
