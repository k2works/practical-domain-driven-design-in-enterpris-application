package com.example.cargotracker.booking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import java.util.Objects;

/**
 * 営業担当者が BR-01 の確定条件を確かめて本予約を確定するコマンド（US-04 AC1・AC2・AC4。Bolt 23・24）。
 * 同じコマンド ID の再送には最初の結果を返す（B-INV-03、ARCH-HO-01）。
 *
 * @param commandId コマンド ID（S-09 を開くたびに発行し、隠し項目で送る）
 * @param transportRequestNumber 予約に使う見積りの業務番号（画面の URL と同じく内部の ID を使わない。D-4、Bolt 23b）
 * @param quotationNo 予約に使う見積りの見積り番号
 * @param operator 操作した利用者（ログインした利用者。役割で B-INV-10 を確かめる）
 * @param staffConfirmed 営業担当者が確定条件を確認したか（BR-01 の 5 つ目の条件）
 */
@Command
public record ConfirmBookingCommand(
        CommandId commandId,
        String transportRequestNumber,
        int quotationNo,
        AuthenticatedActor operator,
        boolean staffConfirmed) {

    public ConfirmBookingCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(operator, "operator");
    }
}
