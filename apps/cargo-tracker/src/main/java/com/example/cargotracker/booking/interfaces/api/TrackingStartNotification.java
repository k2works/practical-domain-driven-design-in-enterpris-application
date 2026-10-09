package com.example.cargotracker.booking.interfaces.api;

/**
 * 追跡の開始の結果の通知（予約の公開 API。ADR-015。Bolt 25）。追跡が DE-22（追跡を開始した）を受けた listener から呼び、予約サガを完了に
 * する。予約 ID で冪等で、処理中の予約サガだけを完了にし、完了済みには何もしない。コマンド ID と期待版（ARCH-HO-01）は持たない（予約 ID で
 * 冪等で、処理中のときだけ完了にするため）。業務の理由で開始できない結果（有人案件の起票）は W8 で足す。
 */
public interface TrackingStartNotification {

    /**
     * 追跡を開始したことを通知する。
     *
     * @param request 通知の内容
     * @return 受領の結果
     */
    TrackingStartNotificationReceipt notifyStarted(TrackingStartNotificationRequest request);
}
