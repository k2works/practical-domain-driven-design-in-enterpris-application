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

    Optional<NotificationRow> selectByEventIdAndRecipient(
            @Param("eventId") UUID eventId, @Param("recipientUserId") UUID recipientUserId);
}
