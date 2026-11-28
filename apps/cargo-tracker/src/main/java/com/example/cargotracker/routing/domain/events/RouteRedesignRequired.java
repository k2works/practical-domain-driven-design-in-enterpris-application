package com.example.cargotracker.routing.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.UUID;

/**
 * DE-06 経路を再設計要にした（US-07 AC4、R-INV-07。Bolt 37d2）。確定した経路版を外部情報の更新で評価して不適合になったときに出す。
 * 業務キーと冪等のキーは経路設計案件 ID と経路版番号。見積りへは経路設計の中の listener が見積りの公開 API を呼んで伝え
 * （荷主の承認・本予約の拒否。Bolt 37e。DE-05 の前例）、予約の再設計中の表示は US-08、追跡の有人案件は W8 が購読する。
 * listener がいない間に出た DE-06 は Spring Modulith のイベントの発行記録に残らない（評価を起こす本番の入口がまだない）。
 * イベントはドメインの型を持たない。
 *
 * @param routingCaseId 経路設計案件 ID
 * @param caseNumber 案件番号（表示用の文字列）
 * @param routeVersionNo 再設計要にした経路版番号
 * @param quotationId 依頼元の見積り ID
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 輸送要求の版番号
 * @param requiredAt 再設計要にした時刻
 * @param reasons 理由の区分の名前（{@code RedesignReasonCode} の名前。重ねない）
 * @param referencedInfoVersions 旧情報版（確定した候補の区間の航海の採用情報版。区間の順）
 */
@DomainEvent
public record RouteRedesignRequired(
        UUID routingCaseId,
        String caseNumber,
        int routeVersionNo,
        UUID quotationId,
        UUID transportRequestId,
        int transportRequestVersionNo,
        UtcInstant requiredAt,
        List<String> reasons,
        List<String> referencedInfoVersions) {

    public RouteRedesignRequired {
        reasons = List.copyOf(reasons);
        referencedInfoVersions = List.copyOf(referencedInfoVersions);
    }
}
