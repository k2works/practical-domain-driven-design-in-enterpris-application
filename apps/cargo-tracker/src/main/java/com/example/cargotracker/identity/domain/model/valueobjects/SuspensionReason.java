package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 停止の理由。システム管理者が利用者の利用を停止するときに選ぶ理由（Bolt 34d。US-16 AC5、UC-16 の 5a）。監査記録
 * {@code USER_SUSPENDED} の理由に名前を残す。自由な文は持たない（2026-10-17、human:kakimomokuri）。
 */
@ValueObject
public enum SuspensionReason {
    /** 利用終了（退職・契約の終了など）。 */
    USAGE_ENDED,
    /** 異動。 */
    TRANSFERRED,
    /** 緊急停止。 */
    EMERGENCY
}
