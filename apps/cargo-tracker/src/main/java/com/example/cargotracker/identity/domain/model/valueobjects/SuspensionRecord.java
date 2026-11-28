package com.example.cargotracker.identity.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * 停止の記録。今の停止（対象の利用者の最新の監査記録 {@code USER_SUSPENDED}）の理由・停止した人・時刻（Bolt 38c2。US-16 AC5、#50）。
 * S-20 の詳細で停止中の利用者に示し、再開してよいかを判断する材料にする。停止と再開の履歴は持たない（監査証跡 S-21 で扱う）。
 *
 * @param reason 停止の理由
 * @param actorUserId 停止した人の利用者 ID
 * @param actorDisplayName 停止した人の今の表示名（利用者の行がなければ空。監査記録の操作者に外部キーはない）
 * @param occurredAt 停止した時刻
 */
@ValueObject
public record SuspensionRecord(
        SuspensionReason reason, UserId actorUserId, Optional<String> actorDisplayName, UtcInstant occurredAt) {

    public SuspensionRecord {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(actorDisplayName, "actorDisplayName");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
