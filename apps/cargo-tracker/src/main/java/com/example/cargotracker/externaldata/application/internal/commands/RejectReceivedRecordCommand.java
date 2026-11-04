package com.example.cargotracker.externaldata.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;
import java.util.UUID;

/**
 * 隔離の却下のコマンド。S-17 の判断の画面で、データ責任者が隔離の受信記録を理由を付けて却下する入力（Bolt 39d2）。理由の必須と長さは
 * 判断の不変条件が守る（画面は先に欄の誤りにする）。
 *
 * @param receivedRecordId 受信記録 ID
 * @param reason 却下の理由
 */
@Command
public record RejectReceivedRecordCommand(UUID receivedRecordId, String reason) {

    public RejectReceivedRecordCommand {
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
    }
}
