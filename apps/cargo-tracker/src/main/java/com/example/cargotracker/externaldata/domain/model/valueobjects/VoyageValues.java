package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 航海の値。航海のファイルの行から読んだ航海番号と寄港の値の並び（Bolt 39b2b）。航海番号は外部 ID の列の値で 1〜30 文字
 * （{@code routing.voyage.voyage_number VARCHAR(30)}。コードポイントで数える）。寄港は 2 つ以上 60 まで（上限は Bolt 39b3）で、
 * 同じ港・同じ到着・同じ出発の寄港を重ねない（同じ港で時刻の違う寄港は周回の航路として受ける）。経路設計の航海（{@code Voyage}）の規則の
 * 写し。ただし寄港の数の上限は、採用値の値の要約（{@code value_summary VARCHAR(4000)}）に入れるための外部データの片側だけの規則で、
 * 経路設計の航海は上限を持たない（意図した食い違い。経路設計に足すかは 39e で決める）。寄港の並びは写しを持つ。
 *
 * @param voyageNumber 航海番号
 * @param portCalls 寄港の値（運航の順。2 つ以上）
 */
@ValueObject
public record VoyageValues(String voyageNumber, List<PortCallValue> portCalls) implements RecordValues {

    /** 航海番号の上限の文字数（コードポイント）。 */
    public static final int VOYAGE_NUMBER_MAX_LENGTH = 30;

    /** 寄港の下限の数（経路設計の航海と同じ）。 */
    public static final int MIN_PORT_CALLS = 2;

    /**
     * 寄港の上限の数（Bolt 39b3 の確認ポイント 5）。寄港 1 つの要約は最大 61 文字で、区切りを入れて 60 なら要約は最大 3719 文字になり、
     * 採用値の値の要約の上限（4000）に収まる。
     */
    public static final int MAX_PORT_CALLS = 60;

    /** 寄港の要約の区切り。 */
    private static final String PORT_CALL_SEPARATOR = "|";

    /** 拒むときは理由（{@link VoyageValuesRejected}）を返す。 */
    public VoyageValues {
        requireVoyageNumber(voyageNumber);
        portCalls = List.copyOf(portCalls);
        if (portCalls.size() < MIN_PORT_CALLS) {
            throw new VoyageValuesRejected(VoyageValuesRejectionReason.TOO_FEW_PORT_CALLS);
        }
        if (portCalls.size() > MAX_PORT_CALLS) {
            throw new VoyageValuesRejected(VoyageValuesRejectionReason.TOO_MANY_PORT_CALLS);
        }
        for (int later = 1; later < portCalls.size(); later++) {
            int earlier = portCalls.subList(0, later).indexOf(portCalls.get(later));
            if (earlier >= 0) {
                throw new VoyageValuesRejected(
                        VoyageValuesRejectionReason.DUPLICATE_PORT_CALLS, List.of(earlier + 1, later + 1));
            }
        }
    }

    /**
     * 要約は寄港を「|」で区切った「UN/LOCODE;到着;出発」の並び（例「JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;」。
     * 空の時刻は空。Bolt 39b3）。航海番号は採用値の対象のキーに書くので、要約に入れない。
     */
    @Override
    public String summary() {
        return portCalls.stream()
                .map(call -> call.port().unLocode() + ";" + RecordValues.summaryTime(call.arrivalAt()) + ";"
                        + RecordValues.summaryTime(call.departureAt()))
                .collect(Collectors.joining(PORT_CALL_SEPARATOR));
    }

    /**
     * 航海番号の規則（空でなく、上限の文字数まで）。寄港の列より先に航海番号を確かめる組み立てが、型の判定をそのまま使う。
     *
     * @param voyageNumber 航海番号
     * @return 規則に合う航海番号（そのまま）
     * @throws VoyageValuesRejected 規則に合わないとき（理由を持つ）
     */
    public static String requireVoyageNumber(String voyageNumber) {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        if (voyageNumber.isBlank()) {
            throw new VoyageValuesRejected(VoyageValuesRejectionReason.VOYAGE_NUMBER_BLANK);
        }
        if (voyageNumber.codePointCount(0, voyageNumber.length()) > VOYAGE_NUMBER_MAX_LENGTH) {
            throw new VoyageValuesRejected(VoyageValuesRejectionReason.VOYAGE_NUMBER_TOO_LONG);
        }
        return voyageNumber;
    }
}
