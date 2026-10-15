package com.example.cargotracker.notification.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/**
 * 通知のリポジトリ（送信ポート。Bolt 33）。実装は infrastructure に置く。
 */
public interface NotificationRepository {

    /**
     * 同じ契機と宛先の通知がまだなければ記録する。記録したら true、すでにあれば false（N-INV-02）。同時の記録で一意制約に当たったときは
     * 例外のまま返す（呼ぶ側のトランザクションを戻す）。
     */
    boolean addIfAbsent(Notification notification);

    /** 送信待ちの通知のときだけ、送信の結果（送信済みか失敗）を書く。書けたら true。 */
    boolean recordOutcome(Notification notification);

    /** 契機と宛先で通知を探す。 */
    Optional<Notification> findByEventIdAndRecipient(UUID eventId, UUID recipientUserId);
}
