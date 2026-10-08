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
    private final List<Object> history = new ArrayList<>();
    private final List<Consumer<Object>> subscribers = new ArrayList<>();
    private List<Object> lastDelivered = List.of();

    public void subscribe(Consumer<Object> subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void publishEvent(Object event) {
        published.add(event);
        history.add(event);
    }

    /**
     * ためたイベントをすべて購読側へ届ける。
     */
    public void deliverAll() {
        List<Object> events = List.copyOf(published);
        published.clear();
        lastDelivered = events;
        events.forEach(event -> subscribers.forEach(subscriber -> subscriber.accept(event)));
    }

    /** 直前に配信したイベントを、もう一度購読側へ届ける（少なくとも 1 回の配信の再配信。受け取りの冪等を確かめる）。 */
    public void redeliverLast() {
        lastDelivered.forEach(event -> subscribers.forEach(subscriber -> subscriber.accept(event)));
    }

    /** 発行されて、まだ配信していないイベント（発行の順）。 */
    public List<Object> published() {
        return List.copyOf(published);
    }

    /** シナリオの始めから発行されたイベント（配信したものを含む。発行の順）。他のコンテキストの公表された言語から値を得るのに使う。 */
    public List<Object> history() {
        return List.copyOf(history);
    }

    public void clear() {
        published.clear();
        history.clear();
        lastDelivered = List.of();
    }
}
