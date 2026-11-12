package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import com.example.cargotracker.quotation.interfaces.api.RouteConditionQuery;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 見積りの公開 API（経路条件の照会）を呼び、経路設計の経路条件に変える腐敗防止層（Bolt 17）。
 * 見積りの型（公開 API の戻り値）は、この部品の外に出さない。貨物種別の名前はここで経路設計の型に翻訳する（D-65、Bolt 39i）。
 */
public class QuotationRouteConditions {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationRouteConditions.class);

    private final RouteConditionQuery query;

    public QuotationRouteConditions(RouteConditionQuery query) {
        this.query = query;
    }

    /** 輸送要求版の経路条件。輸送要求がないか、その版が現在の版でなければ空。 */
    public Optional<RoutingCaseConditions> find(UUID transportRequestId, int transportRequestVersionNo) {
        return query.find(transportRequestId, transportRequestVersionNo)
                .map(view -> new RoutingCaseConditions(
                        view.transportRequestNumber(),
                        new RouteSpecification(
                                view.origin(),
                                view.destination(),
                                view.arrivalDeadline(),
                                cargoCategory(view.cargoCategory())),
                        view.shipperCompanyId()));
    }

    /**
     * 見積りの貨物種別の名前を経路設計の型に写す。知らない名前（見積りに値が増えた、大文字小文字の違い、空）は
     * {@link RouteCargoCategory#UNRECOGNIZED} にして警告を残す。例外にしない（案件を作る依頼の処理が失敗して再配信が続くため。
     * 一般貨物として通すこともしない）。
     */
    static RouteCargoCategory cargoCategory(String quotationName) {
        return switch (quotationName) {
            case "GENERAL" -> RouteCargoCategory.GENERAL;
            case "DANGEROUS" -> RouteCargoCategory.DANGEROUS;
            case "REEFER" -> RouteCargoCategory.REEFER;
            case "OTHER_SPECIAL" -> RouteCargoCategory.OTHER_SPECIAL;
            default -> {
                LOG.warn("知らない貨物種別の名前を UNRECOGNIZED に写します: '{}'", quotationName);
                yield RouteCargoCategory.UNRECOGNIZED;
            }
        };
    }
}
