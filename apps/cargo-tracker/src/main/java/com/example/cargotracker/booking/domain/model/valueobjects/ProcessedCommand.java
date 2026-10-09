package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 処理済みコマンド。本予約の確定に成功したコマンドの記録で、同じコマンド ID の再送に最初の結果を返すのに使う（B-INV-03。Bolt 24）。
 * 貨物予約と同じトランザクションで記録し、拒否した要求は記録しない。内容の照合値で、同じコマンド ID の内容の違う要求（衝突）を見分ける。
 *
 * @param commandId コマンド ID
 * @param payloadHash 内容の照合値（業務番号・見積り番号・操作者の SHA-256 の 16 進）
 * @param bookingId 確定した予約 ID
 * @param trackingNumber 発行した追跡番号
 * @param processedAt 処理時刻（commit 時刻と同じ値）
 */
@ValueObject
public record ProcessedCommand(
        CommandId commandId,
        String payloadHash,
        BookingId bookingId,
        TrackingNumber trackingNumber,
        UtcInstant processedAt) {

    public ProcessedCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(processedAt, "processedAt");
    }

    /** 本予約の確定のコマンドの記録を作る。 */
    public static ProcessedCommand confirmBooking(
            CommandId commandId,
            String transportRequestNumber,
            int quotationNo,
            UserId operator,
            BookingId bookingId,
            TrackingNumber trackingNumber,
            UtcInstant processedAt) {
        return new ProcessedCommand(
                commandId,
                confirmBookingPayloadHash(transportRequestNumber, quotationNo, operator),
                bookingId,
                trackingNumber,
                processedAt);
    }

    /**
     * 本予約の確定のコマンドの内容の照合値。業務番号・見積り番号・操作者を、値に現れない区切り（改行）でつないだ SHA-256 の 16 進。
     * 営業担当者の確認（{@code staffConfirmed}）は、成功した要求では常に確認済みなので入れない。
     */
    public static String confirmBookingPayloadHash(String transportRequestNumber, int quotationNo, UserId operator) {
        String payload = String.join(
                "\n",
                transportRequestNumber,
                Integer.toString(quotationNo),
                operator.value().toString());
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 は Java の実行環境が必ず持つ", e);
        }
    }

    /** 同じ内容のコマンドか。 */
    public boolean sameContentAs(String otherPayloadHash) {
        return payloadHash.equals(otherPayloadHash);
    }
}
