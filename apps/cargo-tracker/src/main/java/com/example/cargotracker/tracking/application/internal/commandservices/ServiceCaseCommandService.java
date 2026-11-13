package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentServiceCaseUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.rules.ServiceDeadlinePolicy;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 有人案件の入力ポート（US-20 AC1・AC2・AC8。Bolt 40-7）。トランザクションの境界になる。起票は認可 → 必須項目 → 重複 → 期限の計算 → 採番 → 保存の順で、
 * 先の段階で止まれば受付番号を使わない（採番は呼び元のトランザクションの中なので、保存が失敗しても戻る）。受領は認可 → 読み出し → 状態 → 更新。
 * 認可の定めは {@link ServiceCaseAuthorization}（台帳 D-159。仮）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class ServiceCaseCommandService {

    /** 期限の計算で営業日カレンダーを読む期間の長さ（日）。登録のない日にかかれば登録漏れとして返す。 */
    private static final int CALENDAR_WINDOW_DAYS = 60;

    private static final ZoneId CALENDAR_ZONE = ZoneId.of("Asia/Tokyo");

    private final ServiceCaseRepository repository;
    private final ServiceCaseNumberIssuer numberIssuer;
    private final BusinessCalendarRepository calendars;
    private final Clock clock;
    private final ServiceDeadlinePolicy deadlinePolicy;

    public ServiceCaseCommandService(
            ServiceCaseRepository repository,
            ServiceCaseNumberIssuer numberIssuer,
            BusinessCalendarRepository calendars,
            Clock clock) {
        this.repository = repository;
        this.numberIssuer = numberIssuer;
        this.calendars = calendars;
        this.clock = clock;
        this.deadlinePolicy = ServiceDeadlinePolicy.provisional();
    }

    /** 有人案件を起票する（US-20 AC1・AC8）。 */
    @Transactional
    public OpenServiceCaseOutcome open(AuthenticatedActor actor, OpenServiceCaseCommand command) {
        if (!ServiceCaseAuthorization.canOpen(actor, command.draft())) {
            return new OpenServiceCaseOutcome.NotPermitted();
        }
        return openUnchecked(command.draft(), actor.userId());
    }

    /**
     * システムが有人案件を起票する（利用者の操作でない内部の経路。拒否した主要実績の起票など。Bolt 40-13）。役割の判定をしない以外は
     * {@link #open} と同じ（必須項目・重複・期限の計算・採番）。
     *
     * @param requestedBy 起票者として記録する利用者（起こした操作の主）
     */
    @Transactional
    public OpenServiceCaseOutcome openBySystem(ServiceCaseDraft draft, UserId requestedBy) {
        return openUnchecked(draft, requestedBy);
    }

    private OpenServiceCaseOutcome openUnchecked(ServiceCaseDraft draft, UserId createdBy) {
        Set<MissingItem> missing = ServiceCase.missingItems(draft);
        if (!missing.isEmpty()) {
            return new OpenServiceCaseOutcome.Incomplete(missing);
        }
        Optional<CaseNumber> duplicate = repository.findUnclosedDuplicate(
                draft.shipperCompanyId(), draft.bookingId(), draft.cause().strip());
        if (duplicate.isPresent()) {
            return new OpenServiceCaseOutcome.Duplicate(duplicate.get());
        }
        Instant now = clock.instant();
        BusinessCalendar calendar;
        ServiceDeadlines deadlines;
        try {
            LocalDate today = now.atZone(CALENDAR_ZONE).toLocalDate();
            calendar = calendars.load(today.minusDays(1), today.plusDays(CALENDAR_WINDOW_DAYS));
            deadlines = deadlinePolicy.deadlinesFor(draft.priority(), now, calendar);
        } catch (BusinessCalendarNotRegisteredException _) {
            return new OpenServiceCaseOutcome.CalendarNotRegistered();
        }
        UtcInstant openedAt = new UtcInstant(now);
        CaseNumber number = numberIssuer.next(now.atZone(calendar.zone()).getYear());
        ServiceCase serviceCase =
                ((ServiceCaseOpening.Opened) ServiceCase.open(draft, number, deadlines, openedAt)).serviceCase();
        repository.save(serviceCase, createdBy);
        return new OpenServiceCaseOutcome.Opened(
                number,
                serviceCase.receiveDueAt(),
                serviceCase.answerDueAt(),
                serviceCase.nextUpdateAt(),
                serviceCase.acknowledgedAt());
    }

    /** 有人案件を受領する（US-20 AC2）。担当者は操作者、受領時刻は時計から得る。 */
    @Transactional
    public ReceiveServiceCaseOutcome receive(AuthenticatedActor actor, ReceiveServiceCaseCommand command) {
        if (!ServiceCaseAuthorization.canReceive(actor)) {
            return new ReceiveServiceCaseOutcome.NotPermitted();
        }
        Optional<ServiceCase> found = repository.findByNumber(command.number());
        if (found.isEmpty()) {
            return new ReceiveServiceCaseOutcome.NotFound();
        }
        ServiceCase serviceCase = found.get();
        ServiceCaseStatus status = serviceCase.status();
        if (status != ServiceCaseStatus.RECEIVED && status != ServiceCaseStatus.ESCALATED) {
            return new ReceiveServiceCaseOutcome.NotReceivable(status);
        }
        ServiceCase received = serviceCase.receive(actor.userId(), new UtcInstant(clock.instant()));
        try {
            repository.update(received, actor.userId());
        } catch (ConcurrentServiceCaseUpdateException _) {
            return new ReceiveServiceCaseOutcome.Conflict();
        }
        return new ReceiveServiceCaseOutcome.Received(command.number());
    }
}
