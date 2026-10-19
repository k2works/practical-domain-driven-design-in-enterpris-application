package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 経路の引き継ぎ。再見積りで新しい見積りに引き継ぐ値（D-85。Bolt 35b）。旧版が再見積りの判定を通ったときだけ旧版が作り、割り当てた経路と荷主の
 * 詳細経路設計の回答を持つ。新しい見積りは輸送要求 ID を照合して受け、社内承認して提示すると荷主承認待ちに直行する（経路設計者の作業を
 * 繰り返さない）。
 *
 * @param transportRequestId 旧版の輸送要求 ID（新しい見積りが照合する）
 * @param route 割り当てた経路
 * @param respondedBy 詳細経路設計へ進むと回答した荷主担当者
 * @param respondedAt 回答時刻
 */
@ValueObject
public record RouteInheritance(
        TransportRequestId transportRequestId, AssignedRoute route, UserId respondedBy, UtcInstant respondedAt) {

    public RouteInheritance {
        Objects.requireNonNull(transportRequestId, "transportRequestId");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(respondedBy, "respondedBy");
        Objects.requireNonNull(respondedAt, "respondedAt");
    }
}
