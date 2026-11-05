package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 取込の値。採用の前に行から読んだ値で、採用値（{@code AdoptedValue}）とは別（Bolt 39b2b）。航海の値か主要実績の値。
 * 取り込む行に載せ、採用値の値の要約の材料になる（Bolt 39b3）。外部原本を持つ受信記録（採用・重複・内容の違う原本の隔離）には、対象のキーと
 * 要約を受信記録の値（{@link ReceivedValue}）として書く（Bolt 39d3。行の原文は payload に残る）。
 */
@ValueObject
public sealed interface RecordValues permits VoyageValues, MilestoneValues {

    /**
     * 値の要約。採用値に書く、値から作った正規の文字列（行の原文ではない。原文は payload に残る）。時刻は UTC の ISO 8601 で、
     * マイクロ秒の 6 桁に固定する（Bolt 39b3 の確認ポイント 4）。
     */
    String summary();

    /**
     * 対象のキー（航海は航海番号、主要実績は追跡番号の値）。採用値と受信記録の値の対象のキーになる（Bolt 39d3）。
     *
     * @return 対象のキー
     */
    String targetKey();

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

    /**
     * 値の要約の時刻を読む（{@link #summaryTime(UtcInstant)} の逆。空は null。Bolt 39d3）。
     *
     * @param text 要約の時刻
     * @return 時刻（空なら null）
     * @throws IllegalArgumentException 要約の時刻の形でないとき（黙って読まない）
     */
    static UtcInstant summaryTimeOf(String text) {
        if (text.isEmpty()) {
            return null;
        }
        // 書き方（summaryTime）と同じ形だけを受ける（マイクロ秒の 6 桁、末尾の Z。日付は暦にあるものだけ）
        DateTimeFormatter format =
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withResolverStyle(ResolverStyle.STRICT);
        try {
            return new UtcInstant(LocalDateTime.parse(text, format).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("要約の時刻の形（UTC のマイクロ秒 6 桁）ではありません: " + text, e);
        }
    }
}
