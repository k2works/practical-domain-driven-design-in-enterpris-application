package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 差し戻すコマンド（US-02 AC2）。
 *
 * @param number 業務番号
 * @param targetVersionNo 審査の対象の版番号（現在の版と照合する。Q-INV-04）
 * @param reviewer 判断者
 * @param reason 理由（必須）
 * @param missingItems 不足事項（任意）
 */
@Command
public record SendBackTransportRequestCommand(
        TransportRequestNumber number, int targetVersionNo, UserId reviewer, String reason, String missingItems) {

    public SendBackTransportRequestCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
