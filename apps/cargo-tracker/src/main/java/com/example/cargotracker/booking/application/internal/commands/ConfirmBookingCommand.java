package com.example.cargotracker.booking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.Objects;
import java.util.UUID;

/**
 * 営業担当者が BR-01 の確定条件を確かめて本予約を確定するコマンド（US-04 AC1・AC2。Bolt 23）。
 * 同じ要求の再送の冪等（コマンド ID、B-INV-03）は Bolt 24 で足す。
 *
 * @param quotationId 予約に使う見積り ID
 * @param operator 操作した利用者（ログインした利用者。役割で B-INV-10 を確かめる）
 * @param staffConfirmed 営業担当者が確定条件を確認したか（BR-01 の 5 つ目の条件）
 */
@Command
public record ConfirmBookingCommand(UUID quotationId, AuthenticatedActor operator, boolean staffConfirmed) {

    public ConfirmBookingCommand {
        Objects.requireNonNull(quotationId, "quotationId");
        Objects.requireNonNull(operator, "operator");
    }
}
