package com.example.cargotracker.routing.infrastructure.config;

import com.example.cargotracker.quotation.interfaces.api.RouteAssignment;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionQuery;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.eventhandlers.QuotationRouteAssignmentEventHandler;
import com.example.cargotracker.routing.application.internal.eventhandlers.RoutingCaseOpeningEventHandler;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.infrastructure.persistence.MyBatisRoutingCaseNumberIssuer;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 経路設計コンテキストの組み立て。アプリケーションサービスに送信アダプターと、見積りの公開 API をつなぐ（Bolt 17）。
 */
@Configuration(proxyBeanMethods = false)
public class RoutingConfiguration {

    /** 見積りの公開 API（経路条件の照会）を経路設計の経路条件に変える腐敗防止層。 */
    @Bean
    QuotationRouteConditions quotationRouteConditions(RouteConditionQuery routeConditionQuery) {
        return new QuotationRouteConditions(routeConditionQuery);
    }

    /** DE-16 を受けて経路設計案件を作る（Spring Modulith のイベントの記録を経て、別のトランザクションで動く）。 */
    @Bean
    RoutingCaseOpeningEventHandler routingCaseOpeningEventHandler(
            RoutingCaseRepository repository,
            RoutingCaseNumberIssuer numberIssuer,
            QuotationRouteConditions routeConditions) {
        return new RoutingCaseOpeningEventHandler(repository, numberIssuer, routeConditions);
    }

    /** 見積りの公開 API（経路の割当て）を呼ぶ腐敗防止層（ADR-014。Bolt 20）。 */
    @Bean
    QuotationRouteAssignments quotationRouteAssignments(RouteAssignment routeAssignment) {
        return new QuotationRouteAssignments(routeAssignment);
    }

    /** DE-05 を受けて見積りに経路版を割り当てる（Spring Modulith のイベントの記録を経て、別のトランザクションで動く。Bolt 20）。 */
    @Bean
    QuotationRouteAssignmentEventHandler quotationRouteAssignmentEventHandler(
            RoutingCaseRepository repository, QuotationRouteAssignments routeAssignments) {
        return new QuotationRouteAssignmentEventHandler(repository, routeAssignments);
    }

    @Bean
    RoutingCaseCommandService routingCaseCommandService(
            RoutingCaseRepository repository,
            VoyageRepository voyageRepository,
            ConnectionRuleRepository connectionRuleRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new RoutingCaseCommandService(
                repository, voyageRepository, connectionRuleRepository, eventPublisher, clock);
    }

    @Bean
    RoutingCaseQueryService routingCaseQueryService(RoutingCaseRepository repository) {
        return new RoutingCaseQueryService(repository);
    }

    /** 起動時に、今年と来年（日本時間）の案件番号の採番の行を用意する（業務番号と同じ。D-13）。 */
    @Bean
    ApplicationRunner routingCaseNumberYearPreparation(MyBatisRoutingCaseNumberIssuer issuer, Clock clock) {
        return args -> {
            int year = RoutingCaseNumber.yearOf(new UtcInstant(clock.instant()));
            issuer.prepareYear(year);
            issuer.prepareYear(year + 1);
        };
    }
}
