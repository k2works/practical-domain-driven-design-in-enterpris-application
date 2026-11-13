package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseDetail;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseListFilter;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseListItem;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.util.List;
import java.util.Optional;

/**
 * 有人案件の画面（S-14 一覧、S-15 詳細）の表示用の値（Bolt 40-11）。業務の判定は照会サービスの結果を示すだけにし、ここでは表示の名前と日時の表記を決める。
 */
final class ServiceCaseViews {

    private ServiceCaseViews() {}

    /** 絞り込みの選択肢（並びは画面の並び）。 */
    record FilterView(String parameter, String label, boolean current) {}

    /** 一覧の 1 行。 */
    record RowView(
            String number,
            String kind,
            String priority,
            String department,
            String status,
            boolean escalated,
            String receiveDueAt,
            boolean receiptOverdue) {}

    /** 進捗の 1 行。 */
    record ProgressView(String updatedAt, String content, String nextUpdateAt, String updatedBy) {}

    /** 回答の 1 行。 */
    record AnswerView(String answeredAt, String content, String answeredBy, String customerResponse) {}

    /** 詳細。 */
    record DetailView(
            String number,
            String kind,
            String priority,
            String department,
            String cause,
            String status,
            boolean escalated,
            boolean receiptOverdue,
            boolean receivable,
            boolean workable,
            boolean closable,
            boolean reopenable,
            String assignee,
            String receivedAt,
            String receiveDueAt,
            String answerDueAt,
            String nextUpdateAt,
            Optional<String> reconciliation,
            List<ProgressView> progress,
            List<AnswerView> answers) {}

    /** 絞り込みの名前（画面の並び: 未終結・未受領・期限超過・対応中）。 */
    static List<FilterView> filters(ServiceCaseListFilter current) {
        return List.of(
                filter(ServiceCaseListFilter.OPEN, "未終結", current),
                filter(ServiceCaseListFilter.UNRECEIVED, "未受領", current),
                filter(ServiceCaseListFilter.OVERDUE, "期限超過", current),
                filter(ServiceCaseListFilter.IN_PROGRESS, "対応中", current));
    }

    private static FilterView filter(ServiceCaseListFilter filter, String label, ServiceCaseListFilter current) {
        return new FilterView(filter.parameter(), label, filter == current);
    }

    /** 件のないときの文（絞り込みごと。理由を示す）。 */
    static String emptyMessage(ServiceCaseListFilter filter) {
        return switch (filter) {
            case OPEN -> "未終結の有人案件はありません。";
            case UNRECEIVED -> "未受領の有人案件はありません。";
            case OVERDUE -> "期限超過の案件はありません。";
            case IN_PROGRESS -> "対応中の有人案件はありません。";
        };
    }

    static RowView row(ServiceCaseListItem item) {
        return new RowView(
                item.number().value(),
                kind(item.kind()),
                priority(item.priority()),
                item.department(),
                status(item.status()),
                item.status() == ServiceCaseStatus.ESCALATED,
                staff(item.receiveDueAt()),
                item.receiptOverdue());
    }

    static DetailView detail(ServiceCaseDetail detail, UserId actor) {
        ServiceCase c = detail.serviceCase();
        boolean receivable = c.status() == ServiceCaseStatus.RECEIVED || c.status() == ServiceCaseStatus.ESCALATED;
        return new DetailView(
                c.number().value(),
                kind(c.kind()),
                priority(c.priority()),
                c.department(),
                c.cause(),
                status(c.status()),
                c.status() == ServiceCaseStatus.ESCALATED,
                detail.receiptOverdue(),
                receivable,
                c.status() == ServiceCaseStatus.IN_PROGRESS,
                c.status() == ServiceCaseStatus.ANSWERED,
                c.status() == ServiceCaseStatus.CLOSED,
                c.assignee().map(id -> assignee(id, actor)).orElse("まだ受領されていません"),
                c.receivedAt().map(ServiceCaseViews::staff).orElse("なし"),
                staff(c.receiveDueAt()),
                staff(c.answerDueAt()),
                staff(c.nextUpdateAt()),
                reconciliation(c),
                c.progress().stream()
                        .map(p -> new ProgressView(
                                staff(p.updatedAt()),
                                p.content(),
                                p.nextUpdateAt().map(ServiceCaseViews::staff).orElse("変更なし"),
                                assignee(p.updatedBy(), actor)))
                        .toList(),
                c.answers().stream()
                        .map(a -> new AnswerView(
                                staff(a.answeredAt()),
                                a.content(),
                                assignee(a.answeredBy(), actor),
                                a.customerResponse().orElse("")))
                        .toList());
    }

    /** 復旧照合の表示（外部停止などで照合が必須のときだけ）。 */
    private static Optional<String> reconciliation(ServiceCase c) {
        if (!c.reconciliationRequired()) {
            return Optional.empty();
        }
        return Optional.of(c.reconciliationId().map(id -> "完了（照合 " + id + "）").orElse("未完了"));
    }

    /** 担当者・更新者の表示。利用者の表示名を引く公開 API がまだないので、本人は「あなた」、ほかは利用者 ID で示す。 */
    private static String assignee(UserId id, UserId actor) {
        return id.equals(actor) ? "あなた" : "利用者 ID: " + id.value();
    }

    static Optional<CaseNumber> parseNumber(String value) {
        try {
            return Optional.of(new CaseNumber(value));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    static String kind(ServiceCaseKind kind) {
        return switch (kind) {
            case CUSTOMER_INQUIRY -> "顧客照会";
            case QUOTATION_CONSULTATION -> "見積り相談";
            case AMENDMENT -> "変更・取消し";
            case REDESIGN -> "経路の再設計";
            case EXTERNAL_OUTAGE -> "外部停止";
            case REJECTED_MILESTONE -> "拒否した実績";
            case SAGA_FAILURE -> "予約サガの失敗";
        };
    }

    static String priority(ServicePriority priority) {
        return switch (priority) {
            case HIGH -> "高";
            case NORMAL -> "通常";
        };
    }

    static String status(ServiceCaseStatus status) {
        return switch (status) {
            case RECEIVED -> "受付済み";
            case ESCALATED -> "上位対応中";
            case IN_PROGRESS -> "対応中";
            case ANSWERED -> "回答済み";
            case CLOSED -> "終結";
            case DUPLICATE_CLOSED -> "重複終了";
        };
    }

    private static String staff(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }
}
