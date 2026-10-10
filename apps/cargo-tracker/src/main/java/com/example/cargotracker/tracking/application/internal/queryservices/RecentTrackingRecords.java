package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Objects;

/**
 * 追跡一覧（S-11 の最小の表示）の照会の結果（Bolt 26）。追跡の開始時刻の新しい順の要約と、上限を超えたか。行は要約をそのまま使い、
 * 同じ形の型を重ねない（Bolt 23b の A-低4）。
 *
 * @param rows 追跡の開始時刻の新しい順の要約（上限まで）
 * @param truncated 上限を超える追跡記録があったか（画面に「新しい N 件だけを示しています」と示す）
 * @param limit 上限の件数（画面の文言に使う。上限の値を 1 か所に置く）
 */
public record RecentTrackingRecords(List<TrackingRecordSummary> rows, boolean truncated, int limit) {

    public RecentTrackingRecords {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }

    /**
     * 上限より 1 件多く引いた要約から、上限までの行と上限を超えたかを作る（S-11 と C-10 で同じ。Bolt 27）。
     *
     * @param found 上限より 1 件多く引いた要約（新しい順）
     * @param limit 上限の件数
     * @return 照会の結果
     */
    public static RecentTrackingRecords of(List<TrackingRecordSummary> found, int limit) {
        return new RecentTrackingRecords(found.subList(0, Math.min(found.size(), limit)), found.size() > limit, limit);
    }
}
