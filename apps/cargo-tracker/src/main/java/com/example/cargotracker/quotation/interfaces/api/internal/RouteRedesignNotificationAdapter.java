package com.example.cargotracker.quotation.interfaces.api.internal;

import com.example.cargotracker.quotation.application.internal.commandservices.RouteRedesignMarking;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteRedesignService;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotification;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import org.springframework.stereotype.Component;

/**
 * 見積りの公開 API（再設計要の伝達）のインバウンドアダプター（ADR-014。Bolt 37e）。依頼を検査して入力ポートに委ね、結果を公開 API の
 * 受領の型に変える。不正な依頼は再配信しても変わらないため、例外にせず理由で返す（{@code RouteAssignmentAdapter} の前例）。
 */
@Component
public class RouteRedesignNotificationAdapter implements RouteRedesignNotification {

    private final RouteRedesignService service;

    public RouteRedesignNotificationAdapter(RouteRedesignService service) {
        this.service = service;
    }

    @Override
    public RouteRedesignNotificationReceipt notifyRedesignRequired(RouteRedesignNotificationRequest request) {
        if (!valid(request)) {
            return new RouteRedesignNotificationReceipt.NotMarked(
                    RouteRedesignNotificationReceipt.NotMarked.INVALID_REQUEST);
        }
        RouteRedesignMarking marking = service.markRedesignRequired(
                request.routingCaseNumber(), request.routeVersionNo(), request.requiredAt());
        if (marking.found() == 0) {
            return new RouteRedesignNotificationReceipt.NotMarked(
                    RouteRedesignNotificationReceipt.NotMarked.QUOTATION_NOT_FOUND);
        }
        return marking.newlyMarked() > 0
                ? new RouteRedesignNotificationReceipt.Marked(marking.newlyMarked())
                : new RouteRedesignNotificationReceipt.AlreadyMarked();
    }

    private static boolean valid(RouteRedesignNotificationRequest request) {
        return request.routingCaseNumber() != null
                && !request.routingCaseNumber().isBlank()
                && request.routeVersionNo() >= 1
                && request.requiredAt() != null;
    }
}
