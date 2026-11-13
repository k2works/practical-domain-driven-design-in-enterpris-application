package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import com.example.cargotracker.tracking.domain.model.entities.CaseProgress;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 有人案件。受付番号を持ち、担当・期限・進捗・回答・復旧照合を追跡する顧客照会または業務例外（Bolt 40-5。BR-09、BR-17、SC-INV-01〜05、US-20）。
 * 集約の境界は案件・進捗・回答。メソッドは新しい {@code ServiceCase} を返し、状態に合わない操作は {@link IllegalStateException} にする。業務上の拒否
 * （必須項目の不足、復旧照合が未完了の終結）は結果の型で返す。永続化は Bolt 40-6、コマンドと認可は 40-7・40-8。
 *
 * <p>状態遷移: 受付済み → 対応中（受領）／上位対応中（受領期限超過）／重複終了。上位対応中 → 対応中。対応中 → 回答済み。回答済み → 終結（復旧照合が必要なら
 * 完了していること）。終結 → 対応中（再開。受付番号・進捗・過去の回答を保持）。
 */
@AggregateRoot
public final class ServiceCase {

    private CaseNumber number;
    private ServiceCaseKind kind;
    private ServicePriority priority;
    private String cause;
    private String department;
    private Optional<CompanyId> shipperCompanyId;
    private Optional<UUID> bookingId;
    private Optional<TrackingNumber> trackingNumber;
    private List<Source> sources;
    private Optional<String> oldVersionRef;
    private Optional<String> newVersionRef;
    private Optional<String> manualSourceRef;
    private boolean reconciliationRequired;
    private Optional<UUID> reconciliationId;
    private Optional<UserId> assignee;
    private Optional<UtcInstant> receivedAt;
    private Optional<UtcInstant> acknowledgedAt;
    private UtcInstant receiveDueAt;
    private UtcInstant answerDueAt;
    private UtcInstant nextUpdateAt;
    private ServiceCaseStatus status;
    private List<CaseProgress> progress;
    private List<CaseAnswer> answers;
    private UtcInstant openedAt;
    private long aggregateVersion;

    private ServiceCase() {}

    private ServiceCase(ServiceCase other) {
        this.number = other.number;
        this.kind = other.kind;
        this.priority = other.priority;
        this.cause = other.cause;
        this.department = other.department;
        this.shipperCompanyId = other.shipperCompanyId;
        this.bookingId = other.bookingId;
        this.trackingNumber = other.trackingNumber;
        this.sources = other.sources;
        this.oldVersionRef = other.oldVersionRef;
        this.newVersionRef = other.newVersionRef;
        this.manualSourceRef = other.manualSourceRef;
        this.reconciliationRequired = other.reconciliationRequired;
        this.reconciliationId = other.reconciliationId;
        this.assignee = other.assignee;
        this.receivedAt = other.receivedAt;
        this.acknowledgedAt = other.acknowledgedAt;
        this.receiveDueAt = other.receiveDueAt;
        this.answerDueAt = other.answerDueAt;
        this.nextUpdateAt = other.nextUpdateAt;
        this.status = other.status;
        this.progress = other.progress;
        this.answers = other.answers;
        this.openedAt = other.openedAt;
        this.aggregateVersion = other.aggregateVersion;
    }

    /**
     * 起票する。種類ごとの必須項目（計画の決定 (3)。台帳 D-158）がそろえば受付済みの案件を、足りなければ不足の項目を返す。
     *
     * @param draft 起票の入力
     * @param number 受付番号（採番は呼び手）
     * @param deadlines 受領・回答の期限と次回更新日時（{@code ServiceDeadlinePolicy} の結果）
     * @param openedAt 起票の時刻
     */
    public static ServiceCaseOpening open(
            ServiceCaseDraft draft, CaseNumber number, ServiceDeadlines deadlines, UtcInstant openedAt) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(deadlines, "deadlines");
        Objects.requireNonNull(openedAt, "openedAt");
        Set<MissingItem> missing = missingOf(draft);
        if (!missing.isEmpty()) {
            return new ServiceCaseOpening.Incomplete(missing);
        }
        ServiceCase serviceCase = new ServiceCase();
        serviceCase.number = number;
        serviceCase.kind = draft.kind();
        serviceCase.priority = draft.priority();
        serviceCase.cause = draft.cause().strip();
        serviceCase.department = draft.department().strip();
        serviceCase.shipperCompanyId = draft.shipperCompanyId();
        serviceCase.bookingId = draft.bookingId();
        serviceCase.trackingNumber = draft.trackingNumber();
        serviceCase.sources = draft.sources();
        serviceCase.oldVersionRef = draft.oldVersionRef();
        serviceCase.newVersionRef = draft.newVersionRef();
        serviceCase.manualSourceRef = draft.manualSourceRef();
        serviceCase.reconciliationRequired =
                draft.reconciliationRequested() || draft.kind() == ServiceCaseKind.EXTERNAL_OUTAGE;
        serviceCase.reconciliationId = Optional.empty();
        serviceCase.assignee = Optional.empty();
        serviceCase.receivedAt = Optional.empty();
        // 営業時間外に起票された（数え始めが起票より後）なら、起票の時刻に受付番号と次回更新日時を返した（US-20 AC8）
        serviceCase.acknowledgedAt = new UtcInstant(deadlines.countedFrom())
                        .instant()
                        .isAfter(openedAt.instant())
                ? Optional.of(openedAt)
                : Optional.empty();
        serviceCase.receiveDueAt = new UtcInstant(deadlines.receiptDue());
        serviceCase.answerDueAt = new UtcInstant(deadlines.responseDue());
        serviceCase.nextUpdateAt = new UtcInstant(deadlines.nextUpdateAt());
        serviceCase.status = ServiceCaseStatus.RECEIVED;
        serviceCase.progress = List.of();
        serviceCase.answers = List.of();
        serviceCase.openedAt = openedAt;
        serviceCase.aggregateVersion = 0;
        return new ServiceCaseOpening.Opened(serviceCase);
    }

    private static Set<MissingItem> missingOf(ServiceCaseDraft draft) {
        Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
        addIf(missing, isBlank(draft.cause()), MissingItem.CAUSE);
        addIf(missing, isBlank(draft.department()), MissingItem.DEPARTMENT);
        missing.addAll(
                switch (draft.kind()) {
                    case CUSTOMER_INQUIRY, QUOTATION_CONSULTATION -> missingForInquiry(draft);
                    case AMENDMENT, REDESIGN -> missingForChange(draft);
                    case EXTERNAL_OUTAGE -> missingForOutage(draft);
                    case REJECTED_MILESTONE -> missingForRejectedMilestone(draft);
                    case SAGA_FAILURE -> missingForSagaFailure(draft);
                });
        return missing;
    }

    private static Set<MissingItem> missingForInquiry(ServiceCaseDraft draft) {
        Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
        addIf(missing, draft.shipperCompanyId().isEmpty(), MissingItem.SHIPPER_COMPANY);
        return missing;
    }

    private static Set<MissingItem> missingForChange(ServiceCaseDraft draft) {
        Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
        addIf(
                missing,
                draft.bookingId().isEmpty() && draft.trackingNumber().isEmpty(),
                MissingItem.BOOKING_OR_TRACKING);
        addIf(missing, isBlank(draft.oldVersionRef().orElse(null)), MissingItem.OLD_VERSION);
        addIf(missing, isBlank(draft.newVersionRef().orElse(null)), MissingItem.NEW_VERSION);
        return missing;
    }

    private static Set<MissingItem> missingForOutage(ServiceCaseDraft draft) {
        Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
        addIf(missing, isBlank(draft.manualSourceRef().orElse(null)), MissingItem.MANUAL_SOURCE);
        return missing;
    }

    private static Set<MissingItem> missingForRejectedMilestone(ServiceCaseDraft draft) {
        Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
        addIf(missing, draft.trackingNumber().isEmpty(), MissingItem.TRACKING_NUMBER);
        addIf(missing, draft.sources().isEmpty(), MissingItem.SOURCES);
        return missing;
    }

    private static Set<MissingItem> missingForSagaFailure(ServiceCaseDraft draft) {
        Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
        addIf(missing, draft.bookingId().isEmpty(), MissingItem.BOOKING);
        return missing;
    }

    private static void addIf(Set<MissingItem> missing, boolean condition, MissingItem item) {
        if (condition) {
            missing.add(item);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * 受領する（受付済み・上位対応中 → 対応中）。担当者と受領時刻を記録する。
     *
     * @throws IllegalStateException 受付済み・上位対応中でない
     */
    public ServiceCase receive(UserId assigneeId, UtcInstant at) {
        Objects.requireNonNull(assigneeId, "assigneeId");
        Objects.requireNonNull(at, "at");
        if (status != ServiceCaseStatus.RECEIVED && status != ServiceCaseStatus.ESCALATED) {
            throw new IllegalStateException("受領できるのは受付済み・上位対応中だけ: " + status);
        }
        ServiceCase next = new ServiceCase(this);
        next.assignee = Optional.of(assigneeId);
        next.receivedAt = Optional.of(at);
        next.status = ServiceCaseStatus.IN_PROGRESS;
        return next;
    }

    /**
     * 受領期限を過ぎた受付済みを上位対応中にする（SC-INV-04、US-20 AC3）。期限ちょうどまでと、受付済みでない案件は変えない（空）。
     *
     * @param now 判定の時刻
     */
    public Optional<ServiceCase> escalate(UtcInstant now) {
        Objects.requireNonNull(now, "now");
        if (status != ServiceCaseStatus.RECEIVED || !now.instant().isAfter(receiveDueAt.instant())) {
            return Optional.empty();
        }
        ServiceCase next = new ServiceCase(this);
        next.status = ServiceCaseStatus.ESCALATED;
        return Optional.of(next);
    }

    /**
     * 進捗を更新する（対応中）。進捗番号を採番して追記し、次回更新日時の指定があれば更新する。
     *
     * @param content 進捗の内容
     * @param nextUpdate 次回更新日時（指定しなければ {@code null} で今のまま）
     * @throws IllegalStateException 対応中でない
     * @throws IllegalArgumentException 内容が空
     */
    public ServiceCase updateProgress(String content, UtcInstant nextUpdate, UserId updatedBy, UtcInstant at) {
        requireInProgress("進捗を更新できる");
        CaseProgress entry =
                new CaseProgress(progress.size() + 1, content, Optional.ofNullable(nextUpdate), updatedBy, at);
        ServiceCase next = new ServiceCase(this);
        List<CaseProgress> appended = new ArrayList<>(progress);
        appended.add(entry);
        next.progress = List.copyOf(appended);
        if (nextUpdate != null) {
            next.nextUpdateAt = nextUpdate;
        }
        return next;
    }

    /**
     * 回答を記録する（対応中 → 回答済み）。回答番号を採番して追記する。
     *
     * @throws IllegalStateException 対応中でない
     */
    public ServiceCase recordAnswer(String content, UserId answeredBy, UtcInstant at) {
        requireInProgress("回答を記録できる");
        CaseAnswer entry =
                new CaseAnswer(answers.size() + 1, content, answeredBy, at, Optional.empty(), Optional.empty());
        ServiceCase next = new ServiceCase(this);
        List<CaseAnswer> appended = new ArrayList<>(answers);
        appended.add(entry);
        next.answers = List.copyOf(appended);
        next.status = ServiceCaseStatus.ANSWERED;
        return next;
    }

    /**
     * 復旧照合の完了を記録する（SC-INV-02）。復旧照合が必要な案件だけ。
     *
     * @param id 照合の ID（外部データの復旧照合）
     * @throws IllegalStateException 復旧照合が要らない、または終結・重複終了している
     */
    public ServiceCase completeReconciliation(UUID id) {
        Objects.requireNonNull(id, "id");
        if (!reconciliationRequired) {
            throw new IllegalStateException("復旧照合が要る案件ではない: " + number.value());
        }
        if (status == ServiceCaseStatus.CLOSED || status == ServiceCaseStatus.DUPLICATE_CLOSED) {
            throw new IllegalStateException("終結した案件には記録できない: " + status);
        }
        ServiceCase next = new ServiceCase(this);
        next.reconciliationId = Optional.of(id);
        return next;
    }

    /**
     * 終結する（回答済み → 終結）。復旧照合が必要で未完了なら終結せず、不足する照合結果を返す（SC-INV-02、US-20 AC7）。
     *
     * @throws IllegalStateException 回答済みでない
     */
    public ServiceCaseClosing close() {
        if (status != ServiceCaseStatus.ANSWERED) {
            throw new IllegalStateException("終結できるのは回答済みだけ: " + status);
        }
        if (reconciliationRequired && reconciliationId.isEmpty()) {
            return new ServiceCaseClosing.ReconciliationIncomplete(Set.of(MissingItem.RECONCILIATION_RESULT));
        }
        ServiceCase next = new ServiceCase(this);
        next.status = ServiceCaseStatus.CLOSED;
        return new ServiceCaseClosing.Closed(next);
    }

    /**
     * 再開する（終結 → 対応中）。受付番号・進捗・過去の回答を保持し、顧客の回答（未解決）を最後の回答に記録する（SC-INV-03、US-20 AC6）。
     *
     * @param customerResponse 顧客の回答
     * @throws IllegalStateException 終結でない
     */
    public ServiceCase reopen(String customerResponse, UtcInstant at) {
        Objects.requireNonNull(at, "at");
        if (status != ServiceCaseStatus.CLOSED) {
            throw new IllegalStateException("再開できるのは終結だけ: " + status);
        }
        List<CaseAnswer> updated = new ArrayList<>(answers);
        int last = updated.size() - 1;
        updated.set(last, updated.get(last).withCustomerResponse(customerResponse, at));
        ServiceCase next = new ServiceCase(this);
        next.answers = List.copyOf(updated);
        next.status = ServiceCaseStatus.IN_PROGRESS;
        return next;
    }

    /**
     * 重複と判定して終える（受付済み → 重複終了）。
     *
     * @throws IllegalStateException 受付済みでない
     */
    public ServiceCase markDuplicate() {
        if (status != ServiceCaseStatus.RECEIVED) {
            throw new IllegalStateException("重複終了にできるのは受付済みだけ: " + status);
        }
        ServiceCase next = new ServiceCase(this);
        next.status = ServiceCaseStatus.DUPLICATE_CLOSED;
        return next;
    }

    private void requireInProgress(String operation) {
        if (status != ServiceCaseStatus.IN_PROGRESS) {
            throw new IllegalStateException("対応中のときだけ" + operation + ": " + status);
        }
    }

    public CaseNumber number() {
        return number;
    }

    public ServiceCaseKind kind() {
        return kind;
    }

    public ServicePriority priority() {
        return priority;
    }

    public String cause() {
        return cause;
    }

    public String department() {
        return department;
    }

    public Optional<CompanyId> shipperCompanyId() {
        return shipperCompanyId;
    }

    public Optional<UUID> bookingId() {
        return bookingId;
    }

    public Optional<TrackingNumber> trackingNumber() {
        return trackingNumber;
    }

    public List<Source> sources() {
        return List.copyOf(sources);
    }

    public Optional<String> oldVersionRef() {
        return oldVersionRef;
    }

    public Optional<String> newVersionRef() {
        return newVersionRef;
    }

    public Optional<String> manualSourceRef() {
        return manualSourceRef;
    }

    public boolean reconciliationRequired() {
        return reconciliationRequired;
    }

    public Optional<UUID> reconciliationId() {
        return reconciliationId;
    }

    public Optional<UserId> assignee() {
        return assignee;
    }

    public Optional<UtcInstant> receivedAt() {
        return receivedAt;
    }

    /** 営業時間外に起票され、受付番号と次回更新日時を直ちに返した時刻（営業時間内は空）。 */
    public Optional<UtcInstant> acknowledgedAt() {
        return acknowledgedAt;
    }

    public UtcInstant receiveDueAt() {
        return receiveDueAt;
    }

    public UtcInstant answerDueAt() {
        return answerDueAt;
    }

    public UtcInstant nextUpdateAt() {
        return nextUpdateAt;
    }

    public ServiceCaseStatus status() {
        return status;
    }

    public List<CaseProgress> progress() {
        return progress;
    }

    public List<CaseAnswer> answers() {
        return answers;
    }

    public UtcInstant openedAt() {
        return openedAt;
    }

    /** 楽観ロックの版（保存された版。起票した案件は 0。更新のたびにリポジトリが 1 増やす）。 */
    public long aggregateVersion() {
        return aggregateVersion;
    }

    /**
     * 保存されている状態から組み立てる（リポジトリが使う。Bolt 40-6）。起票の入力の確かめは通さず、保存した値をそのまま持つ。
     */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static ServiceCase reconstitute(
            CaseNumber number,
            ServiceCaseKind kind,
            ServicePriority priority,
            String cause,
            String department,
            Optional<CompanyId> shipperCompanyId,
            Optional<UUID> bookingId,
            Optional<TrackingNumber> trackingNumber,
            List<Source> sources,
            Optional<String> oldVersionRef,
            Optional<String> newVersionRef,
            Optional<String> manualSourceRef,
            boolean reconciliationRequired,
            Optional<UUID> reconciliationId,
            Optional<UserId> assignee,
            Optional<UtcInstant> receivedAt,
            Optional<UtcInstant> acknowledgedAt,
            UtcInstant receiveDueAt,
            UtcInstant answerDueAt,
            UtcInstant nextUpdateAt,
            ServiceCaseStatus status,
            List<CaseProgress> progress,
            List<CaseAnswer> answers,
            UtcInstant openedAt,
            long aggregateVersion) {
        ServiceCase serviceCase = new ServiceCase();
        serviceCase.number = Objects.requireNonNull(number, "number");
        serviceCase.kind = Objects.requireNonNull(kind, "kind");
        serviceCase.priority = Objects.requireNonNull(priority, "priority");
        serviceCase.cause = Objects.requireNonNull(cause, "cause");
        serviceCase.department = Objects.requireNonNull(department, "department");
        serviceCase.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        serviceCase.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        serviceCase.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        serviceCase.sources = List.copyOf(sources);
        serviceCase.oldVersionRef = Objects.requireNonNull(oldVersionRef, "oldVersionRef");
        serviceCase.newVersionRef = Objects.requireNonNull(newVersionRef, "newVersionRef");
        serviceCase.manualSourceRef = Objects.requireNonNull(manualSourceRef, "manualSourceRef");
        serviceCase.reconciliationRequired = reconciliationRequired;
        serviceCase.reconciliationId = Objects.requireNonNull(reconciliationId, "reconciliationId");
        serviceCase.assignee = Objects.requireNonNull(assignee, "assignee");
        serviceCase.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        serviceCase.acknowledgedAt = Objects.requireNonNull(acknowledgedAt, "acknowledgedAt");
        serviceCase.receiveDueAt = Objects.requireNonNull(receiveDueAt, "receiveDueAt");
        serviceCase.answerDueAt = Objects.requireNonNull(answerDueAt, "answerDueAt");
        serviceCase.nextUpdateAt = Objects.requireNonNull(nextUpdateAt, "nextUpdateAt");
        serviceCase.status = Objects.requireNonNull(status, "status");
        serviceCase.progress = List.copyOf(progress);
        serviceCase.answers = List.copyOf(answers);
        serviceCase.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        serviceCase.aggregateVersion = aggregateVersion;
        return serviceCase;
    }
}
