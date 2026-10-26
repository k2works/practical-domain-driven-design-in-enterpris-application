package com.example.cargotracker.notification.application.internal.commandservices;

import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** メールの送信のサービスのテストの偽物（再設定と利用者の招待で共有する。Bolt 38b2b）。 */
final class NotificationTestDoubles {

    private NotificationTestDoubles() {}

    static final class RecordingSender implements MailSender {
        final List<OutgoingMail> sent = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void send(OutgoingMail mail) {
            if (failure != null) {
                throw failure;
            }
            sent.add(mail);
        }
    }

    static final class InMemoryNotifications implements NotificationRepository {
        private final Map<String, Notification> rows = new HashMap<>();
        boolean rejectOutcome;

        private static String key(UUID eventId, UUID recipient) {
            return eventId + "/" + recipient;
        }

        Optional<Notification> find(UUID eventId, UUID recipientUserId) {
            return findByEventIdAndRecipient(eventId, recipientUserId);
        }

        @Override
        public boolean addIfAbsent(Notification notification) {
            return rows.putIfAbsent(key(notification.eventId(), notification.recipientUserId()), notification) == null;
        }

        @Override
        public boolean recordOutcome(Notification notification) {
            String key = key(notification.eventId(), notification.recipientUserId());
            Notification current = rows.get(key);
            if (rejectOutcome || current == null || current.status() != NotificationStatus.PENDING) {
                return false;
            }
            rows.put(key, notification);
            return true;
        }

        @Override
        public Optional<Notification> findByEventIdAndRecipient(UUID eventId, UUID recipientUserId) {
            return Optional.ofNullable(rows.get(key(eventId, recipientUserId)));
        }
    }

    /** トランザクションを開かずにそのまま呼ぶ（単体テスト用）。 */
    static final class ImmediateTransactions extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
