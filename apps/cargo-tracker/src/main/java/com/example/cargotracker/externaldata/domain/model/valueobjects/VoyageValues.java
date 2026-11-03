package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 航海の値。航海のファイルの行から読んだ航海番号と寄港の値の並び（Bolt 39b2b）。航海番号は外部 ID の列の値で 1〜30 文字
 * （{@code routing.voyage.voyage_number VARCHAR(30)}。コードポイントで数える）。寄港は 2 つ以上で、同じ港・同じ到着・同じ出発の寄港を
 * 重ねない（同じ港で時刻の違う寄港は周回の航路として受ける）。経路設計の航海（{@code Voyage}）の規則の写し。寄港の並びは写しを持つ。
 *
 * @param voyageNumber 航海番号
 * @param portCalls 寄港の値（運航の順。2 つ以上）
 */
@ValueObject
public record VoyageValues(String voyageNumber, List<PortCallValue> portCalls) implements RecordValues {

    /** 航海番号の上限の文字数（コードポイント）。 */
    private static final int VOYAGE_NUMBER_MAX_LENGTH = 30;

    private static final int MIN_PORT_CALLS = 2;

    public VoyageValues {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        if (voyageNumber.isBlank()) {
            throw new IllegalArgumentException("航海番号が空です");
        }
        if (voyageNumber.codePointCount(0, voyageNumber.length()) > VOYAGE_NUMBER_MAX_LENGTH) {
            throw new IllegalArgumentException("航海番号が " + VOYAGE_NUMBER_MAX_LENGTH + " 文字より長い: " + voyageNumber);
        }
        portCalls = List.copyOf(portCalls);
        if (portCalls.size() < MIN_PORT_CALLS) {
            throw new IllegalArgumentException("寄港が足りません（" + MIN_PORT_CALLS + " つ以上）: " + voyageNumber);
        }
        if (new HashSet<>(portCalls).size() < portCalls.size()) {
            throw new IllegalArgumentException("同じ寄港があります: " + voyageNumber);
        }
    }
}
