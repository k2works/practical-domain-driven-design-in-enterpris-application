package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.util.List;
import java.util.Optional;

/**
 * 拒否した主要実績を有人例外案件にする（T-INV-04、BR-16。Bolt 40-13。台帳 D-160）。案件の種類は「拒否した実績」、優先度は通常、担当部門は
 * 追跡管理部。内容は理由と出典で一意にし、同じ拒否の二重の起票を重複の判定で防ぐ。追跡記録があれば（取消済みの予約）荷主企業と予約を残す。
 *
 * <p>起票できないとき（営業カレンダーが未登録、必須項目の不足）は例外を上げる。利用者の操作ではなく購読の中の処理なので、呼び元の
 * トランザクションを戻し、設定を直した後の再配信で起票する。
 */
public class RejectedMilestoneCaseOpener implements RejectionCaseOpener {

    private static final String DEPARTMENT = "追跡管理部";

    private final SystemServiceCaseOpener commands;

    public RejectedMilestoneCaseOpener(SystemServiceCaseOpener commands) {
        this.commands = commands;
    }

    /** 起票元キー。拒否した出典（種類と参照。拒否の記録は出典ごとに 1 件）で一意。 */
    private static String originRef(RejectedMilestone rejected) {
        return "REJECTED_MILESTONE:" + rejected.source().kind() + ":"
                + rejected.source().reference();
    }

    @Override
    public void open(RejectedMilestone rejected, Optional<TrackingRecord> trackingRecord, UserId operator) {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.REJECTED_MILESTONE,
                        ServicePriority.NORMAL,
                        "拒否した主要実績（理由: " + rejected.reason() + "、出典: "
                                + rejected.source().kind() + ":"
                                + rejected.source().reference() + "）",
                        DEPARTMENT)
                .withTracking(rejected.trackingNumber())
                .withSources(List.of(rejected.source()));
        if (trackingRecord.isPresent()) {
            draft = draft.withShipper(trackingRecord.get().shipperCompanyId())
                    .withBooking(trackingRecord.get().bookingId());
        }
        switch (commands.open(draft, operator, originRef(rejected))) {
            case OpenServiceCaseOutcome.Opened _, OpenServiceCaseOutcome.Duplicate _ -> {
                // 起票した、または同じ拒否で起票済み
            }
            case OpenServiceCaseOutcome.CalendarNotRegistered _ ->
                throw new IllegalStateException("営業カレンダーが未登録のため、拒否した主要実績の有人例外案件を起票できない");
            case OpenServiceCaseOutcome.Incomplete incomplete ->
                throw new IllegalStateException("拒否した主要実績の有人例外案件の必須項目が足りない: " + incomplete.missing());
            case OpenServiceCaseOutcome.NotPermitted _ -> throw new IllegalStateException("システムの起票が権限で拒まれた（起こり得ない）");
        }
    }
}
