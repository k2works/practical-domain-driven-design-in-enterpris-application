package com.example.cargotracker.tracking.interfaces.web;

/**
 * C-10 追跡の照会の追跡番号の入力（Bolt 27）。誤りがあっても入力値をそのまま画面に戻せるよう、変換前の文字列を持つ（UI 設計の共通部品
 * 「フォーム項目」）。入力がない（一覧を開いただけ）ときは null。
 */
public class TrackingNumberForm {

    private String trackingNumber;

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
}
