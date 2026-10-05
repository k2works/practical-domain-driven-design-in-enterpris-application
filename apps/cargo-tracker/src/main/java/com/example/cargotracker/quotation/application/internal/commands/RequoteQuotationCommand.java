package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;

/**
 * 見積りを再見積りするコマンド（US-03 AC5、Q-INV-07・18。Bolt 11）。旧版を置換済み（有効期限を過ぎていれば失効）にし、
 * 次の見積り番号の新しい見積りを作って算出する。
 *
 * @param number 業務番号
 * @param quotationNo 置き換える見積りの見積り番号
 * @param input 新しい見積りの入力（料金明細・通貨・有効期限・経路方針）
 */
@Command
public record RequoteQuotationCommand(TransportRequestNumber number, int quotationNo, QuotationInput input) {

    public RequoteQuotationCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(input, "input");
    }
}
