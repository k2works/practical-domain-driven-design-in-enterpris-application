package com.example.cargotracker.booking.interfaces.api;

/**
 * 輸送段階の通知（予約の公開 API。ADR-014。Bolt 39g2）。追跡が DE-09（集荷実績を採用した）・DE-10（引渡しを確認した）を受けた listener から呼び、
 * 予約の状態と輸送段階を進める。予約 ID と通知の種類で冪等で、段は戻さない（後から届いた古い通知は記録済み。B-INV-12 の版の比べに代える）。
 * コマンド ID と期待版（ARCH-HO-01）は持たない（前例 {@link TrackingStartNotification}）。
 */
public interface TransportPhaseNotification {

    /**
     * 集荷実績を採用したことを通知する。確定・集荷前の予約を輸送中・集荷後に進める。
     *
     * @param request 通知の内容
     * @return 受領の結果。業務の理由で記録しなかったときも例外にせず {@code NotRecorded} で返す
     * @throws RuntimeException 予約の行のロックの待ちの上限を超えたときなどの技術の失敗（呼ぶ側は例外のまま戻し、再配信に任せる）
     */
    TransportPhaseNotificationReceipt notifyPickedUp(TransportPhaseNotificationRequest request);

    /**
     * 引渡しを確認したことを通知する。確定または輸送中の予約を完了・完了に進める。
     *
     * @param request 通知の内容
     * @return 受領の結果。業務の理由で記録しなかったときも例外にせず {@code NotRecorded} で返す
     * @throws RuntimeException 予約の行のロックの待ちの上限を超えたときなどの技術の失敗（呼ぶ側は例外のまま戻し、再配信に任せる）
     */
    TransportPhaseNotificationReceipt notifyDelivered(TransportPhaseNotificationRequest request);
}
