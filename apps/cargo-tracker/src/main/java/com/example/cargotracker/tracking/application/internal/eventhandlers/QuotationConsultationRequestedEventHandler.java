package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.QuotationConsultationRequested;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.application.internal.commandservices.OpenServiceCaseOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.SystemServiceCaseOpener;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 荷主が見積りの条件を相談した を購読し、相談内容を伴う有人案件（種類: 見積り相談）を起票する（US-24 AC3。W9 の Bolt 46-1）。見積りは追跡に
 * 依存せず、追跡がイベントを購読する（ADR-014 の方向。DE-32 の購読と同じ形）。起票した案件は、荷主の問い合わせ一覧（C-14）に載り、荷主が進捗と
 * 回答を追える（US-23）。見積りの状態は変えない。
 *
 * <p>起票元キーは見積り ID と相談の時刻で、イベントの再配信で二重に起票しない。起票できないとき（営業カレンダーが未登録など）は例外を上げ、
 * 発行記録を未完了のまま再配信に任せる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class QuotationConsultationRequestedEventHandler {

    private static final String DEPARTMENT = "営業部";

    private final SystemServiceCaseOpener commands;

    public QuotationConsultationRequestedEventHandler(SystemServiceCaseOpener commands) {
        this.commands = commands;
    }

    @ApplicationModuleListener
    public void on(QuotationConsultationRequested event) {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.QUOTATION_CONSULTATION,
                        ServicePriority.NORMAL,
                        "見積り相談: " + event.transportRequestNumber() + " 見積 " + event.quotationNo() + "（"
                                + event.content() + "）",
                        DEPARTMENT)
                .withShipper(new CompanyId(event.shipperCompanyId()));
        String originRef = "QUOTATION_CONSULTATION:" + event.quotationId() + ":"
                + event.requestedAt().instant().toEpochMilli();
        switch (commands.open(draft, new UserId(event.requestedBy()), originRef)) {
            case OpenServiceCaseOutcome.Opened _, OpenServiceCaseOutcome.Duplicate _ -> {
                // 起票した、または未終結の同じ相談の案件がある
            }
            case OpenServiceCaseOutcome.CalendarNotRegistered _ ->
                throw new IllegalStateException("営業カレンダーが未登録のため、見積り相談の有人案件を起票できない: " + event.transportRequestNumber());
            case OpenServiceCaseOutcome.Incomplete incomplete ->
                throw new IllegalStateException("見積り相談の有人案件の必須項目が足りない: " + incomplete.missing());
            case OpenServiceCaseOutcome.NotPermitted _ -> throw new IllegalStateException("システムの起票が権限で拒まれた（起こり得ない）");
        }
    }
}
