package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 停止の記録の読み出しの 1 行（Bolt 38c2）。対象の利用者の停止と再開のうち最新の監査記録の操作（{@code USER_SUSPENDED} か
 * {@code USER_REACTIVATED}）・理由（停止の理由の名前）・操作者・時刻に、操作者の今の表示名を足したもの（操作者の利用者の行がなければ
 * 表示名は null）。
 */
public record SuspensionRecordRow(
        String action, String reason, UUID actorUserId, String actorDisplayName, OffsetDateTime occurredAt) {

    /** 今の停止の記録として読めるか。最新が再開の行か、理由か操作者の欠けた行（DB を直接書き換えた）なら読めない。 */
    boolean isCurrentSuspension() {
        return "USER_SUSPENDED".equals(action) && reason != null && actorUserId != null;
    }
}
