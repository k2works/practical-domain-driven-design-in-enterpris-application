package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import java.util.Objects;

/**
 * 経路設計案件を作るときに見積りから得る条件（Bolt 17）。経路設計のドメインの型に変えた後の値。
 *
 * @param transportRequestNumber 業務番号の表記
 * @param specification 経路条件
 */
public record RoutingCaseConditions(String transportRequestNumber, RouteSpecification specification) {

    public RoutingCaseConditions {
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(specification, "specification");
    }
}
