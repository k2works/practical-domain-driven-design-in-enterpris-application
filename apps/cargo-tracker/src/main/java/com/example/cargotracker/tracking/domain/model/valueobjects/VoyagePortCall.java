package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * 航海の寄港。DE-12 が運ぶ航海の採用値の寄港の 1 つを追跡の言葉にした写し（港・到着・出発。Bolt 39g4b）。listener が DE-12 の寄港の並びから作り、追跡記録は
 * 外部データの型を持たない。経路設計の {@code PortCall}・外部データの {@code AdoptedPortCall} と同じ単純名にしない。
 *
 * @param port 港
 * @param arrivalAt 到着（なければ空）
 * @param departureAt 出発（なければ空）
 */
@ValueObject
public record VoyagePortCall(Location port, Optional<UtcInstant> arrivalAt, Optional<UtcInstant> departureAt) {

    public VoyagePortCall {
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        Objects.requireNonNull(departureAt, "departureAt");
    }

    /**
     * 届いた寄港の値から作る（港が欠けていれば内容の欠けとして扱う）。
     *
     * @param port 港
     * @param arrivalAt 到着（なければ null）
     * @param departureAt 出発（なければ null）
     * @throws VoyageArrival.InvalidEventValueException 港が null
     */
    public static VoyagePortCall of(Location port, UtcInstant arrivalAt, UtcInstant departureAt) {
        if (port == null) {
            throw new VoyageArrival.InvalidEventValueException("portCalls.port");
        }
        return new VoyagePortCall(port, Optional.ofNullable(arrivalAt), Optional.ofNullable(departureAt));
    }
}
