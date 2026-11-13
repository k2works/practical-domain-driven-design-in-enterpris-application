package com.example.cargotracker.tracking.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * 電話・メールで受けた照会を記録するコマンド（US-11 AC2、IR-INV-02。Bolt 40-15）。
 *
 * @param channel 経路
 * @param trackingNumber 追跡番号（荷主企業は追跡記録から決める）
 * @param inquirerKind 照会者の種別
 * @param receivedAt 受付時刻（指定がなければ記録の時刻）
 * @param content 照会内容（引き継ぐときは有人案件の内容になる）
 * @param handOff 有人案件へ引き継ぐか
 * @param sourceReference 参照した出典の参照（任意。引き継ぐとき有人案件の出典になる）
 * @param department 担当部門（引き継ぐとき）
 */
@Command
public record RecordInquiryCommand(
        InquiryChannel channel,
        TrackingNumber trackingNumber,
        InquirerKind inquirerKind,
        Optional<UtcInstant> receivedAt,
        String content,
        boolean handOff,
        Optional<String> sourceReference,
        String department) {

    public RecordInquiryCommand {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(inquirerKind, "inquirerKind");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sourceReference, "sourceReference");
        Objects.requireNonNull(department, "department");
    }
}
