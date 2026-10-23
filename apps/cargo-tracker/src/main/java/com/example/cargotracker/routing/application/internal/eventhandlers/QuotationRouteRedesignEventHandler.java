package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteRedesigns;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-06 経路を再設計要にした を経路設計の中で受け、見積りの公開 API でその経路版を割り当てた見積りに再設計要の印を付ける（US-07 AC5、
 * Q-INV-10、B-INV-05、ADR-014。Bolt 37e2）。見積りは経路設計のイベントを購読しない（依存が循環する。D-71）。値は DE-06 の項目から
 * 取り、案件を読まない。評価のコミットの後に、非同期で、新しいトランザクションの中で処理する。伝達は冪等なので、再配信はそのまま
 * 呼び直す。業務の理由で付けなかった（経路をまだ割り当てていないなど）ときは、警告のログを残して終える。経路をまだ割り当てていなければ、
 * DE-05 の listener が割り当てた後に続けて伝える（届く順 B）。見積りの楽観ロックの競合は上げて、発行記録を未完了に残す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class QuotationRouteRedesignEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationRouteRedesignEventHandler.class);

    private final QuotationRouteRedesigns routeRedesigns;

    public QuotationRouteRedesignEventHandler(QuotationRouteRedesigns routeRedesigns) {
        this.routeRedesigns = routeRedesigns;
    }

    @ApplicationModuleListener
    public void on(RouteRedesignRequired event) {
        routeRedesigns
                .notifyRedesignRequired(
                        RoutingCaseNumber.parse(event.caseNumber()), event.routeVersionNo(), event.requiredAt())
                .ifPresent(reason -> LOG.warn(
                        "DE-06 の再設計要を見積りに伝えなかった: 案件 {}、経路版 {}、理由 {}" + "（経路をまだ割り当てていなければ DE-05 の listener が続けて伝える）",
                        event.caseNumber(),
                        event.routeVersionNo(),
                        reason));
    }
}
