package com.example.cargotracker.externaldata.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;
import java.util.UUID;

/**
 * 隔離の採用のコマンド。S-17 の判断の画面で、データ責任者が隔離の受信記録を採用する入力（Bolt 39d3b）。理由は任意で、長さは判断の不変条件が
 * 守る（画面は先に欄の誤りにする）。
 *
 * @param receivedRecordId 受信記録 ID
 * @param reason 採用の理由（任意）
 */
@Command
public record AdoptReceivedRecordCommand(UUID receivedRecordId, String reason) {

    public AdoptReceivedRecordCommand {
        Objects.requireNonNull(receivedRecordId, "receivedRecordId");
    }
}
