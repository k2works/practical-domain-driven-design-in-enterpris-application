package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 案件番号。経路設計者と営業が言い合える経路設計案件の番号で、{@code RC-年-年ごとの連番} と表記する（D-4・D-10 と同じ規則。Bolt 17）。
 * 連番は 4 桁のゼロ埋めで、9999 を超えたら桁を増やす。
 *
 * @param year 年（詳細経路設計の依頼時刻の日本時間の年）
 * @param sequence 年ごとの連番（1 から）
 */
@ValueObject
public record RoutingCaseNumber(int year, int sequence) {

    /** 案件番号の年を決めるタイムゾーン（業務番号と同じ。D-10）。 */
    private static final ZoneId NUMBERING_ZONE = ZoneId.of("Asia/Tokyo");

    private static final Pattern TEXT = Pattern.compile("RC-(\\d{4})-(\\d{4,})");

    public RoutingCaseNumber {
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("案件番号の年は 4 桁です: " + year);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("案件番号の連番は 1 から始まります: " + sequence);
        }
    }

    /** 詳細経路設計の依頼時刻から案件番号の年を決める。年は日本時間（Asia/Tokyo）で区切る。 */
    public static int yearOf(UtcInstant requestedAt) {
        return requestedAt.instant().atZone(NUMBERING_ZONE).getYear();
    }

    /** 表記から案件番号を読む。ゼロ埋めの桁が表記の規則と違うもの（例: {@code RC-2026-00001}）は読まない。 */
    public static RoutingCaseNumber parse(String text) {
        Matcher matcher = TEXT.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("案件番号の形式（RC-年-連番）ではありません: " + text);
        }
        RoutingCaseNumber number =
                new RoutingCaseNumber(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        if (!number.text().equals(text)) {
            throw new IllegalArgumentException("案件番号の連番の桁が表記の規則と違います: " + text);
        }
        return number;
    }

    /** 案件番号の表記（例: {@code RC-2026-0001}）。 */
    public String text() {
        return "RC-%d-%04d".formatted(year, sequence);
    }
}
