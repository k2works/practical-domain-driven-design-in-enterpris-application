package com.example.cargotracker.quotation.application.internal.commands;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;

/**
 * 審査を確定するコマンド（US-02 AC1）。
 *
 * @param number 業務番号
 * @param targetVersionNo 審査の対象の版番号（画面で見ていた版。現在の版と照合する。Q-INV-04）
 * @param reviewer 判断者
 * @param rationale 根拠（必須）
 */
@Command
public record ApproveTransportRequestCommand(
        TransportRequestNumber number, int targetVersionNo, UserId reviewer, String rationale) {

    public ApproveTransportRequestCommand {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
