package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 輸送要求の一覧（社内の受付一覧 S-02、荷主の見積依頼の一覧 C-02）の 1 行の読み取りモデル。集約を組み立てずに一覧するための写しで、業務の規則は持たない（Bolt 5 レビュー R-09）。
 *
 * @param number 業務番号
 * @param versionNo 現在の版番号
 * @param status 状態
 * @param firstSubmittedAt 最初の提出時刻（版 1。KPI-01 の開始と同じ。並びの基準）
 * @param currentSubmittedAt 現在の版の提出時刻
 * @param origin 出発地
 * @param destination 目的地
 * @param arrivalDeadline 希望到着期限
 * @param cargoCategory 貨物種別
 */
public record TransportRequestSummary(
        TransportRequestNumber number,
        int versionNo,
        TransportRequestStatus status,
        UtcInstant firstSubmittedAt,
        UtcInstant currentSubmittedAt,
        Location origin,
        Location destination,
        UtcInstant arrivalDeadline,
        CargoCategory cargoCategory) {

    public TransportRequestSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(firstSubmittedAt, "firstSubmittedAt");
        Objects.requireNonNull(currentSubmittedAt, "currentSubmittedAt");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline");
        Objects.requireNonNull(cargoCategory, "cargoCategory");
    }
}
