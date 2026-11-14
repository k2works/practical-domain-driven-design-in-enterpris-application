package com.example.cargotracker.notification.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知の MyBatis マッパー（Bolt 33）。SQL は同じパッケージの NotificationMapper.xml に置く。更新は条件付きの 1 文で、更新した件数を返す。
 */
@Mapper
public interface NotificationMapper {

    int insertNotificationIfAbsent(NotificationRow row);

    int updateOutcomeIfPending(NotificationRow row);

    /** 宛先の利用者の通知を、新しい順に上限まで（種類で絞る。Bolt 43-3）。 */
    java.util.List<NotificationRow> selectRecentByRecipient(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("kinds") java.util.List<String> kinds,
            @Param("limit") int limit);

    Optional<NotificationRow> selectByEventIdAndRecipient(
            @Param("eventId") UUID eventId, @Param("recipientUserId") UUID recipientUserId);
}
