package com.example.cargotracker.shared.acceptance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 業務ルール層の受入シナリオで使う、テスト用の同期の配信（テスト戦略）。
 * 発行されたイベントをためておき、シナリオの「配信される」ステップで購読側へ届ける。
 * 本番では発行元のコミット後に配信されるため、発行と配信をステップとして分ける。
 */
public class DeferredEventDelivery implements ApplicationEventPublisher {

    private final List<Object> published = new ArrayList<>();
    private final List<Consumer<Object>> subscribers = new ArrayList<>();

    public void subscribe(Consumer<Object> subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void publishEvent(Object event) {
        published.add(event);
    }

    /**
     * ためたイベントをすべて購読側へ届ける。
     */
    public void deliverAll() {
        List<Object> events = List.copyOf(published);
        published.clear();
        events.forEach(event -> subscribers.forEach(subscriber -> subscriber.accept(event)));
    }

    /** 発行されて、まだ配信していないイベント（発行の順）。 */
    public List<Object> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
