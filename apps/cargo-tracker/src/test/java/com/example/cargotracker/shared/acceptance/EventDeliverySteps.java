package com.example.cargotracker.shared.acceptance;

import io.cucumber.java.ja.もし;

/**
 * 共通ステップ: ドメインイベントの配信。
 */
public class EventDeliverySteps {

    private final DeferredEventDelivery delivery;

    public EventDeliverySteps(DeferredEventDelivery delivery) {
        this.delivery = delivery;
    }

    @もし("発行されたドメインイベントが購読するコンテキストに配信される")
    public void 発行されたドメインイベントが配信される() {
        delivery.deliverAll();
    }
}
