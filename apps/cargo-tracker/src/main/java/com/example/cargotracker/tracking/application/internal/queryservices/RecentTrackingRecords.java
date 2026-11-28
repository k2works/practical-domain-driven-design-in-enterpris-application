package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 追跡一覧（S-11 の最小の表示）の照会の結果（Bolt 26）。追跡の開始時刻の新しい順の要約と、上限を超えたか。行は要約をそのまま使い、
 * 同じ形の型を重ねない（Bolt 23b の A-低4）。照会したときに鮮度超過の追跡番号も持つ（S-11 の印。Bolt 39g4。荷主の C-10 は持たない）。
 *
 * @param rows 追跡の開始時刻の新しい順の要約（上限まで）
 * @param truncated 上限を超える追跡記録があったか（画面に「新しい N 件だけを示しています」と示す）
 * @param limit 上限の件数（画面の文言に使う。上限の値を 1 か所に置く）
 * @param freshnessExceeded 照会したときに鮮度超過の行の追跡番号（FRS-02。Bolt 39g4）
 */
public record RecentTrackingRecords(
        List<TrackingRecordSummary> rows, boolean truncated, int limit, Set<TrackingNumber> freshnessExceeded) {

    public RecentTrackingRecords {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        freshnessExceeded = Set.copyOf(Objects.requireNonNull(freshnessExceeded, "freshnessExceeded"));
    }

    /** 鮮度の判定をしない照会の結果（荷主の C-10。Bolt 39g4 の前の形）。 */
    public RecentTrackingRecords(List<TrackingRecordSummary> rows, boolean truncated, int limit) {
        this(rows, truncated, limit, Set.of());
    }

    /**
     * 上限より 1 件多く引いた要約から、上限までの行と上限を超えたかを作る（S-11 と C-10 で同じ。Bolt 27）。鮮度の判定はしない。
     *
     * @param found 上限より 1 件多く引いた要約（新しい順）
     * @param limit 上限の件数
     * @return 照会の結果
     */
    public static RecentTrackingRecords of(List<TrackingRecordSummary> found, int limit) {
        return of(found, limit, summary -> false);
    }

    /**
     * 上限より 1 件多く引いた要約から、上限までの行と上限を超えたかと、行ごとの鮮度の判定を作る（S-11。Bolt 39g4）。
     *
     * @param found 上限より 1 件多く引いた要約（新しい順）
     * @param limit 上限の件数
     * @param exceeded 行が鮮度超過か
     * @return 照会の結果
     */
    public static RecentTrackingRecords of(
            List<TrackingRecordSummary> found, int limit, Predicate<TrackingRecordSummary> exceeded) {
        List<TrackingRecordSummary> rows = found.subList(0, Math.min(found.size(), limit));
        return new RecentTrackingRecords(
                rows,
                found.size() > limit,
                limit,
                rows.stream()
                        .filter(exceeded)
                        .map(TrackingRecordSummary::trackingNumber)
                        .collect(Collectors.toSet()));
    }

    /** 行が照会したときに鮮度超過か（Bolt 39g4）。 */
    public boolean freshnessExceeded(TrackingRecordSummary row) {
        return freshnessExceeded.contains(row.trackingNumber());
    }
}
