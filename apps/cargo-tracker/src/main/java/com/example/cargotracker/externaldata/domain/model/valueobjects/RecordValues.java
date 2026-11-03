package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 取込の値。採用の前に行から読んだ値で、採用値（{@code AdoptedValue}）とは別（Bolt 39b2b）。航海の値か主要実績の値。
 * 取り込む行に載せ、受信記録の列には書かない（値は payload の行の原文に残る）。採用値の値の要約の材料になる（Bolt 39b3）。
 */
@ValueObject
public sealed interface RecordValues permits VoyageValues, MilestoneValues {

    /**
     * 値の要約。採用値に書く、値から作った正規の文字列（行の原文ではない。原文は payload に残る）。時刻は UTC の ISO 8601 で、
     * マイクロ秒の 6 桁に固定する（Bolt 39b3 の確認ポイント 4）。
     */
    String summary();

    /**
     * 値の要約の時刻の書き方（UTC の ISO 8601、マイクロ秒の 6 桁に固定。例「2026-11-19T23:00:00.000000Z」。ないときは空）。
     *
     * @param time 時刻（ないときは null）
     * @return 要約の時刻
     */
    static String summaryTime(UtcInstant time) {
        if (time == null) {
            return "";
        }
        LocalDateTime utc = LocalDateTime.ofInstant(time.instant(), ZoneOffset.UTC);
        return "%sT%02d:%02d:%02d.%06dZ"
                .formatted(utc.toLocalDate(), utc.getHour(), utc.getMinute(), utc.getSecond(), utc.getNano() / 1_000);
    }
}
