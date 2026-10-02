package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 業務番号。荷主と社内が電話・メールで言い合える輸送要求の番号で、{@code TR-年-年ごとの連番} と表記する（D-4、D-10）。
 * 連番は 4 桁のゼロ埋めで、9999 を超えたら桁を増やす。
 *
 * @param year 年（提出時刻の日本時間の年）
 * @param sequence 年ごとの連番（1 から）
 */
@ValueObject
public record TransportRequestNumber(int year, int sequence) {

    /** 業務番号の年を決めるタイムゾーン（D-10）。 */
    private static final ZoneId NUMBERING_ZONE = ZoneId.of("Asia/Tokyo");

    private static final Pattern TEXT = Pattern.compile("TR-(\\d{4})-(\\d{4,})");

    public TransportRequestNumber {
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("業務番号の年は 4 桁です: " + year);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("業務番号の連番は 1 から始まります: " + sequence);
        }
    }

    /**
     * 提出時刻から業務番号の年を決める。年は日本時間（Asia/Tokyo）で区切る。
     */
    public static int yearOf(UtcInstant submittedAt) {
        return submittedAt.instant().atZone(NUMBERING_ZONE).getYear();
    }

    /**
     * 表記から業務番号を読む。ゼロ埋めの桁が表記の規則と違うもの（例: {@code TR-2026-00001}）は読まない。
     */
    public static TransportRequestNumber parse(String text) {
        Matcher matcher = TEXT.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("業務番号の形式（TR-年-連番）ではありません: " + text);
        }
        TransportRequestNumber number =
                new TransportRequestNumber(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        if (!number.text().equals(text)) {
            throw new IllegalArgumentException("業務番号の連番の桁が表記の規則と違います: " + text);
        }
        return number;
    }

    /** 業務番号の表記（例: {@code TR-2026-0001}）。 */
    public String text() {
        return "TR-%d-%04d".formatted(year, sequence);
    }
}
