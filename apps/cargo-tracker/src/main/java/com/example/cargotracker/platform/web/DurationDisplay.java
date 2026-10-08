package com.example.cargotracker.platform.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 期間表示の部品（UI 設計の共通部品「日時表示」の節の期間の形）。画面ごとに 3 つの形がある。見積り・経路設計・アクセス監査の写しを、
 * 振る舞いを変えずに集めた（Bolt 22、#41）。形をそろえるかは US-21 の残り（W9）で決める（D-84）。
 */
public final class DurationDisplay {

    private DurationDisplay() {}

    /** 受付一覧（S-02）の待ち時間（例: 3 時間 20 分、2 日 4 時間、15 分）。分未満は切り捨て、負は 0 分にする。 */
    public static String waiting(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        long days = minutes / (24 * 60);
        long hours = minutes % (24 * 60) / 60;
        long restMinutes = minutes % 60;
        if (days > 0) {
            return days + " 日 " + hours + " 時間";
        }
        if (hours > 0) {
            return hours + " 時間 " + restMinutes + " 分";
        }
        return restMinutes + " 分";
    }

    /** 経路設計（S-06）の接続時間・接続余裕（例: 1 日 12 時間、4 時間、4 時間 30 分、0 分）。負でない期間を渡す。 */
    public static String connection(Duration duration) {
        long days = duration.toDays();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + " 日");
        }
        if (hours > 0) {
            parts.add(hours + " 時間");
        }
        if (minutes > 0 || parts.isEmpty()) {
            parts.add(minutes + " 分");
        }
        return String.join(" ", parts);
    }

    /** KPI（S-22）のリードタイム・経過時間（例: 26 時間 30 分）。日に繰り上げず、分未満は切り捨てる（Bolt 21）。 */
    public static String hoursAndMinutes(Duration duration) {
        return duration.toHours() + " 時間 " + duration.toMinutesPart() + " 分";
    }
}
