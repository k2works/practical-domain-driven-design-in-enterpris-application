package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotification;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;

/**
 * 見積りの公開 API（再設計要の伝達）を呼ぶ腐敗防止層（ADR-014。Bolt 37e2）。経路設計の値を公開 API の形に変え、結果を経路設計の言葉
 * （付けなかった理由）にして返す。見積りの型（公開 API の引数と戻り値）は、この部品の外に出さない。DE-06 の listener と、DE-05 の
 * listener の続けての伝達が使う。
 */
public class QuotationRouteRedesigns {

    private final RouteRedesignNotification notification;

    public QuotationRouteRedesigns(RouteRedesignNotification notification) {
        this.notification = notification;
    }

    /**
     * 経路版を割り当てた見積りに再設計要の印を付ける。
     *
     * @return 付けなかった理由（付けたか、すでに付いていたら空）
     */
    public Optional<String> notifyRedesignRequired(
            RoutingCaseNumber number, int routeVersionNo, UtcInstant requiredAt) {
        RouteRedesignNotificationReceipt receipt = notification.notifyRedesignRequired(
                new RouteRedesignNotificationRequest(number.text(), routeVersionNo, requiredAt));
        return switch (receipt) {
            case RouteRedesignNotificationReceipt.Marked _, RouteRedesignNotificationReceipt.AlreadyMarked _ ->
                Optional.empty();
            case RouteRedesignNotificationReceipt.NotMarked(String reason) -> Optional.of(reason);
        };
    }
}
