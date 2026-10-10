package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.Objects;

/**
 * 追跡管理者が出典と発生時刻を指定して主要実績を登録するコマンド（US-12 AC1・AC2。Bolt 26b）。
 *
 * @param trackingNumber 追跡番号
 * @param expectedVersion 画面を開いたときの追跡記録の版（違えば競合）
 * @param kind 実績の種類
 * @param location 場所
 * @param occurredAt 発生時刻
 * @param source 出典（取得時刻は呼び出し元が決める。Bolt 26b の確認ポイント 15）
 * @param registrant 登録者（ログインした利用者）
 */
@Command
public record RegisterMilestoneCommand(
        TrackingNumber trackingNumber,
        long expectedVersion,
        MilestoneKind kind,
        Location location,
        UtcInstant occurredAt,
        Source source,
        UserId registrant) {

    public RegisterMilestoneCommand {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(registrant, "registrant");
    }
}
