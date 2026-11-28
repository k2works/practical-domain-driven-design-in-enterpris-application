package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;

/**
 * 採用した航海の値。DE-12 が運ぶ航海の採用値を経路設計の言葉にした写し（Bolt 39e）。listener が DE-12 から作り、航海に適用する。
 * 航海は外部データの型を持たない。必須の値が欠けていれば {@link InvalidEventValueException}（再配信しても直らない、内容の欠けたイベント）。
 *
 * @param voyageNumber 航海番号
 * @param portCalls 寄港（運航の順。2 つ以上）
 * @param infoVersion 情報版（提供元の外部版）
 * @param source 出典（種類、受信記録 ID、取得時刻）
 * @param publishedAt 採用値を公開した時刻
 */
@ValueObject
public record VoyageAdoption(
        String voyageNumber, List<PortCall> portCalls, String infoVersion, Source source, UtcInstant publishedAt) {

    private static final int MIN_PORT_CALLS = 2;

    public VoyageAdoption {
        requiredText(voyageNumber, "voyageNumber");
        if (portCalls == null || portCalls.size() < MIN_PORT_CALLS) {
            throw new InvalidEventValueException("portCalls");
        }
        portCalls = List.copyOf(portCalls);
        requiredText(infoVersion, "infoVersion");
        required(source, "source");
        required(publishedAt, "publishedAt");
    }

    /** 届いた寄港の値から寄港を作る（寄港の規則に合わなければ内容の欠けとして扱う）。 */
    public static PortCall portCall(Location port, UtcInstant arrivalAt, UtcInstant departureAt) {
        required(port, "port");
        try {
            return new PortCall(port, arrivalAt, departureAt);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventValueException("portCalls: " + e.getMessage());
        }
    }

    private static void required(Object value, String name) {
        if (value == null) {
            throw new InvalidEventValueException(name);
        }
    }

    private static void requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventValueException(name);
        }
    }

    /** 届いたイベントの値が欠けている、または規則に合わない（内容の欠けたイベント。listener はこの型だけを捨てる）。 */
    public static final class InvalidEventValueException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        /** @param detail 欠けている値の名前と理由（ログに出す） */
        public InvalidEventValueException(String detail) {
            super("航海の採用値の内容が欠けている: " + detail);
        }
    }
}
