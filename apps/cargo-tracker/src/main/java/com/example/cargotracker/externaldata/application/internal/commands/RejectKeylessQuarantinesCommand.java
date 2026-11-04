package com.example.cargotracker.externaldata.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;
import java.util.UUID;

/**
 * 1 つの取込の形式の誤りのまとめての却下のコマンド。S-17 のまとめての却下の画面で、データ責任者がその取込の判断を待つキーのない隔離を
 * すべて 1 つの理由で却下する入力（Bolt 39d2b、【業務の判断】(g)）。理由の必須と長さは受信記録ごとの判断の不変条件が守る（画面は先に欄の
 * 誤りにする）。
 *
 * @param ingestionId 取込 ID
 * @param reason 却下の理由（全件に付ける）
 */
@Command
public record RejectKeylessQuarantinesCommand(UUID ingestionId, String reason) {

    public RejectKeylessQuarantinesCommand {
        Objects.requireNonNull(ingestionId, "ingestionId");
    }
}
