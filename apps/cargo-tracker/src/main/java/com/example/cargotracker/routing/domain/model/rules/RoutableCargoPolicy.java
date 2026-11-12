package com.example.cargotracker.routing.domain.model.rules;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.util.List;

/**
 * 経路設計できる貨物（BR-02、BR-03、R-INV-02。Bolt 39i）。MVP の経路設計の対象は一般貨物だけで、一般貨物でない案件は候補をすべて
 * 「貨物種別に対応しない」で除外する。知らない種別も除外する（一般貨物として通さない）。航海に貨物の対応の属性がないので、候補ごとには
 * 見分けない。算出のときだけ判定する（確定と評価は貨物種別を判定し直さない。貨物種別は輸送要求版の写しで算出の後に変わらない）。
 * 後続段階で特殊貨物を受け付けるときは、この規則を差し替える（OQ-03）。
 */
@DomainRule
public final class RoutableCargoPolicy {

    private RoutableCargoPolicy() {}

    /**
     * 判定する。不適合の時刻は最初の区間の出発予定、参照情報版は最初の区間の航海の採用情報版。
     *
     * @param category 案件の貨物種別
     * @param legs 候補の区間の列
     * @return 貨物種別の理由（一般貨物と、区間のない候補は空）
     */
    public static List<ExclusionReason> evaluate(RouteCargoCategory category, List<Leg> legs) {
        if (category.general() || legs.isEmpty()) {
            return List.of();
        }
        Leg first = legs.getFirst();
        return List.of(ExclusionReason.cargoNotSupported(category, first.departureAt(), first.infoVersion()));
    }
}
