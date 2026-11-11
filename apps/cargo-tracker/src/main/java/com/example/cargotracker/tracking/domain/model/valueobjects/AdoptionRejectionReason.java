package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 採用の拒否の理由。追跡管理者の下書きの採用を受け付けない理由（Bolt 39gb）。採用の拒否は永続化しないので、拒否の記録の {@link MilestoneRejectionReason} と分ける。
 */
@ValueObject
public enum AdoptionRejectionReason {
    /** 候補でない（置き換えられた下書き、採用済み・保持のみ・手入力の実績、ない実績番号）。 */
    NOT_CANDIDATE,
    /** 採用済みの実績と値が違う（採用すると T-INV-06 の二者確認を素通りする。値を直す道は US-13）。 */
    CONFLICTS_WITH_ADOPTED,
    /** 取消済みの予約（T-INV-04）。 */
    BOOKING_CANCELLED,
    /** 完了後（予約状態が完了か、現在状態が引渡し済み。T-INV-05）。 */
    TRACKING_COMPLETED,
    /** 発生時刻が採用時刻より後（T-INV-13 の採用の分）。 */
    OCCURRED_IN_FUTURE
}
