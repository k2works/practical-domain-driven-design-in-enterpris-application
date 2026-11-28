package com.example.cargotracker.booking.application.internal.commandservices;

/**
 * 集荷・引渡しの通知を受けたときの予約の結果（ADR-014。Bolt 39g2）。予約の公開 API の実装が、公開 API の受領の型に変える。
 */
public enum TransportPhaseOutcome {
    /** 予約の状態と輸送段階を進めて保存した。 */
    RECORDED,
    /** すでに記録済み（同じ通知の再実行か、後から届いた古い通知。何もしない）。 */
    ALREADY_RECORDED,
    /** 予約がない（再配信で直らない欠け）。 */
    BOOKING_NOT_FOUND,
    /** 取消済みの予約。 */
    BOOKING_CANCELLED,
    /** 変更・取消しの申請中の予約（W11 で決め直す）。 */
    BOOKING_UNDER_REQUEST
}
