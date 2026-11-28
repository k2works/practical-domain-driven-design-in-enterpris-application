package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.routing.application.internal.commands.EvaluateConfirmedRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.ConfirmedRouteEvaluationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.domain.events.VoyageUpdated;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-30 航海を更新した を購読し、その航海を確定の区間に持つ案件を評価する（US-07 AC4。Bolt 39f の決定 (4)・(5)）。航海の更新のコミットの後に、非同期で、新しいトランザクションの中で
 * 処理する。案件は 1 つのトランザクションで評価する（評価の入力ポートのトランザクションに参加する）。
 *
 * <p>評価が競合を受けたら同じトランザクションで読み直して評価し直す（3 回まで。読み直しの直前に 0 件の UPDATE があるので MyBatis の 1 次キャッシュは消えている）。先の評価が再設計要に
 * していれば読み直しの結果は「変わらない」で、DE-06 は重ならない。3 回とも競合したら例外を上げて全体を戻し、発行記録に残して再起動の再配信に任せる。照会の後に案件がなくなっていたら、
 * WARN のログを残して次の案件へ進む。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class ConfirmedRouteEvaluationEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmedRouteEvaluationEventHandler.class);

    /** 評価を試みる回数（読み直しの上限。{@link VoyageUpdateEventHandler} と同じ）。 */
    private static final int MAX_ATTEMPTS = 3;

    private final RoutingCaseRepository repository;
    private final RoutingCaseCommandService commandService;

    public ConfirmedRouteEvaluationEventHandler(
            RoutingCaseRepository repository, RoutingCaseCommandService commandService) {
        this.repository = repository;
        this.commandService = commandService;
    }

    @ApplicationModuleListener
    public void on(VoyageUpdated event) {
        repository.findCaseNumbersConfirmedOnVoyage(event.voyageNumber()).forEach(number -> evaluate(number, event));
    }

    private void evaluate(RoutingCaseNumber number, VoyageUpdated event) {
        EvaluateConfirmedRouteCommand command = new EvaluateConfirmedRouteCommand(number);
        String caseNumber = number.text();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ConfirmedRouteEvaluationOutcome outcome = commandService.evaluateConfirmedRoute(command);
            if (outcome instanceof ConfirmedRouteEvaluationOutcome.NotFound) {
                LOG.warn("確定した経路を評価しなかった（照会の後に案件がない）: 案件 {}、航海 {}", caseNumber, event.voyageNumber());
                return;
            }
            if (!(outcome instanceof ConfirmedRouteEvaluationOutcome.Conflict)) {
                return;
            }
        }
        throw new IllegalStateException(
                "確定した経路の評価が " + MAX_ATTEMPTS + " 回とも競合した: 案件 " + caseNumber + "、航海 " + event.voyageNumber());
    }
}
