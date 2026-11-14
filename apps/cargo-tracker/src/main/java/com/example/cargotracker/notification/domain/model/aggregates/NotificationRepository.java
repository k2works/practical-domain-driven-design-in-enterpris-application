package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * 宛先の利用者の通知を、新しい順に上限まで返す（荷主の通知一覧 C-16。US-22 AC4。W9 の Bolt 43-3）。
     *
     * @param kinds 返す種類（一覧に出すのは荷主への業務の通知だけ。再設定や招待のメールは出さない）
     * @param limit 返す最大の件数
     */
    List<Notification> findRecentByRecipient(UUID recipientUserId, Set<NotificationKind> kinds, int limit);

    /** 契機と宛先で通知を探す。 */
    Optional<Notification> findByEventIdAndRecipient(UUID eventId, UUID recipientUserId);
}
