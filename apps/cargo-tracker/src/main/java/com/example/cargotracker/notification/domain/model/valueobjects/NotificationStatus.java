package com.example.cargotracker.notification.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 通知の状態。送信待ちからだけ、送信済みか失敗に変わる（データモデル `notification.status`）。
 */
@ValueObject
public enum NotificationStatus {
    /** 記録して、送信の結果を待っている。 */
    PENDING,
    /** 送った。 */
    SENT,
    /** 送れなかった（理由を残す）。 */
    FAILED
}
