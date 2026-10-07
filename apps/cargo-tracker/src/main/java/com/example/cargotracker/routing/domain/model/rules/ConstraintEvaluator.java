package com.example.cargotracker.routing.domain.model.rules;

import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 制約適合判定。経路条件、区間の列、接続時間規則、判定時刻から、適合・除外と理由を返す純粋な関数（R-INV-01・02、BR-11）。
 * Golden dataset（TST-02、W6）で検証する。
 *
 * <p>到着予定が希望到着期限以前で、すべての接続時間が必要最小接続時間以上なら適合（同時刻・同値は適合）。
 * 接続時間は、前の区間の到着予定から次の区間の出発予定まで。積替えの港に判定時刻で有効な規則がなければ、確かめていない接続を
 * 通さないよう「接続できない」で除外する（失敗に倒す。Bolt 17 の確認ポイント 9）。同じ港に有効な規則が複数あれば厳しい方を使う。
 * 理由は区間の順の接続の理由、期限超過の順に並べる。貨物種別は R0.1 では判定しない（確認ポイント 10）。
 */
@DomainRule
public class ConstraintEvaluator {

    /** 判定する。 */
    public ConstraintEvaluation evaluate(
            RouteSpecification specification, List<Leg> legs, List<ConnectionRule> rules, UtcInstant judgedAt) {
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("区間のない候補は判定できません");
        }
        List<ExclusionReason> reasons = new ArrayList<>();
        Duration minSlack = null;
        for (int i = 1; i < legs.size(); i++) {
            Leg previous = legs.get(i - 1);
            Leg next = legs.get(i);
            Location port = previous.discharge();
            Optional<Duration> required = requiredConnection(rules, port, judgedAt);
            if (required.isEmpty()) {
                reasons.add(ExclusionReason.notConnectable(next.departureAt(), port, next.infoVersion()));
                continue;
            }
            Duration connection = Duration.between(
                    previous.arrivalAt().instant(), next.departureAt().instant());
            Duration slack = connection.minus(required.get());
            if (minSlack == null || slack.compareTo(minSlack) < 0) {
                minSlack = slack;
            }
            if (slack.isNegative()) {
                reasons.add(ExclusionReason.connectionTooShort(
                        next.departureAt(), port, required.get(), next.infoVersion()));
            }
        }
        Leg last = legs.getLast();
        UtcInstant deadline = specification.arrivalDeadline();
        if (last.arrivalAt().instant().isAfter(deadline.instant())) {
            reasons.add(ExclusionReason.deadlineExceeded(last.arrivalAt(), deadline, last.infoVersion()));
        }
        return new ConstraintEvaluation(last.arrivalAt(), reasons, minSlack);
    }

    private static Optional<Duration> requiredConnection(
            List<ConnectionRule> rules, Location port, UtcInstant judgedAt) {
        return rules.stream()
                .filter(rule -> rule.appliesTo(port, judgedAt))
                .map(ConnectionRule::minimumConnection)
                .max(Comparator.naturalOrder());
    }
}
