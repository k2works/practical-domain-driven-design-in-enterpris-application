package com.example.cargotracker.booking.application.internal.commandservices;

/**
 * 追跡の開始の結果を受けたときの予約サガの結果（ADR-015。Bolt 25）。予約の公開 API の実装が、公開 API の受領の型に変える。
 */
public enum TrackingStartOutcome {
    /** 処理中の予約サガを完了にした。 */
    COMPLETED,
    /** すでに完了していた（同じ結果の再通知。何もしない）。 */
    ALREADY_COMPLETED,
    /** 予約サガがない（再配信で直らない欠け）。 */
    SAGA_NOT_FOUND
}
