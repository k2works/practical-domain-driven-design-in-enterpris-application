package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import java.util.Optional;

/**
 * 拒否した主要実績の有人例外案件への起票（アプリケーション層の協力者。T-INV-04。Bolt 40-13。外部の技術のポートではないので、アプリケーション層に置く。W8 の週全体レビュー C で置き直した）。追跡が DE-13 を実績にせず拒否の記録に残したとき、同じ
 * トランザクションの中で呼ぶ。起票できなければ例外を上げ、拒否の記録も残さずに再配信に任せる。
 */
@FunctionalInterface
public interface RejectionCaseOpener {

    /**
     * 拒否した主要実績を有人例外案件として起票する。同じ拒否で起票済みなら何もしない。
     *
     * @param rejected 拒否した主要実績
     * @param trackingRecord 追跡記録（取消済みの予約のときだけある。荷主企業と予約を案件に残す）
     * @param operator DE-13 の操作者（案件の起票者として記録する）
     * @throws IllegalStateException 起票できない（営業カレンダーが未登録など）
     */
    void open(RejectedMilestone rejected, Optional<TrackingRecord> trackingRecord, UserId operator);
}
