package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.routing.domain.events.RouteCandidatesNotFound;
import com.example.cargotracker.tracking.application.internal.commandservices.OpenServiceCaseOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-32 適合する経路候補が見つからなかった を購読し、有人案件を起票する（UC-06 3a、US-20。Bolt 40-14。台帳 D-161）。経路設計は追跡に依存せず、
 * 追跡がイベントを購読する（ADR-014 の方向）。理由が基本制約を満たす候補なしなら種類は見積り相談（優先度 通常）、外部データの欠落なら
 * 外部停止（優先度 高。復旧照合が要る。手動の情報源として経路設計案件を残す）。内容に経路設計案件の番号を入れ、未終結の同じ案件の二重の起票を
 * 重複の判定で防ぐ（再算出のたびにイベントが出るため）。
 *
 * <p>起票できないとき（営業カレンダーが未登録など）は例外を上げ、発行記録を未完了のまま再配信に任せる。知らない理由は組み立て違いなので
 * 例外にする。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class RouteCandidatesNotFoundEventHandler {

    private static final String DEPARTMENT = "経路設計部";

    private final ServiceCaseCommandService commands;

    public RouteCandidatesNotFoundEventHandler(ServiceCaseCommandService commands) {
        this.commands = commands;
    }

    @ApplicationModuleListener
    public void on(RouteCandidatesNotFound event) {
        switch (commands.openBySystem(draft(event), event.operatorUserId())) {
            case OpenServiceCaseOutcome.Opened _, OpenServiceCaseOutcome.Duplicate _ -> {
                // 起票した、または未終結の同じ案件がある
            }
            case OpenServiceCaseOutcome.CalendarNotRegistered _ ->
                throw new IllegalStateException("営業カレンダーが未登録のため、経路候補なしの有人案件を起票できない: " + event.caseNumber());
            case OpenServiceCaseOutcome.Incomplete incomplete ->
                throw new IllegalStateException("経路候補なしの有人案件の必須項目が足りない: " + incomplete.missing());
            case OpenServiceCaseOutcome.NotPermitted _ -> throw new IllegalStateException("システムの起票が権限で拒まれた（起こり得ない）");
        }
    }

    private static ServiceCaseDraft draft(RouteCandidatesNotFound event) {
        return switch (event.reason()) {
            case "NO_CONFORMING_CANDIDATE" ->
                ServiceCaseDraft.of(
                                ServiceCaseKind.QUOTATION_CONSULTATION,
                                ServicePriority.NORMAL,
                                "経路候補なし: 経路設計案件 " + event.caseNumber() + " の候補の算出で、基本制約を満たす候補がない（見つけた候補 "
                                        + event.candidatesFound() + " 件）。専門判断を要する",
                                DEPARTMENT)
                        .withShipper(event.shipperCompanyId());
            case "EXTERNAL_DATA_MISSING" ->
                ServiceCaseDraft.of(
                                ServiceCaseKind.EXTERNAL_OUTAGE,
                                ServicePriority.HIGH,
                                "経路候補なし: 経路設計案件 " + event.caseNumber() + " の候補の算出で、経路に使える航海の外部データがない。取込を確かめる",
                                DEPARTMENT)
                        .withShipper(event.shipperCompanyId())
                        .withManualSource("経路設計案件 " + event.caseNumber() + "（経路に使える航海の情報がない）");
            default -> throw new IllegalArgumentException("知らない理由: " + event.reason());
        };
    }
}
