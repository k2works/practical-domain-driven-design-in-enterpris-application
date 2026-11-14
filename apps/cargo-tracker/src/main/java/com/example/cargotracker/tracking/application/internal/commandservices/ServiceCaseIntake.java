package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.rules.ServiceDeadlinePolicy;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 有人案件の起票の共通の手順（Bolt 40-7・40-13・40-17・40-18）。利用者の起票（{@link ServiceCaseCommandService}。認可を通す）とシステムの起票
 * （{@link SystemServiceCaseOpener}。認可を通らない内部の経路）が共有する。必須項目 → 重複 → 期限の計算 → 採番 → 保存の順で、先の段階で止まれば
 * 受付番号を使わない。このクラスは認可を持たない（呼び手が通す）ので、同じパッケージの外には出さない。
 *
 * <p>営業カレンダーが未登録のとき、システムの起票（起票元キーがある）は既定のカレンダー（平日 9:00〜18:00、土日休み、祝日なし）で期限を
 * 計算して続け（イベントが毒メッセージにならない。W8 の週全体レビュー B。台帳 D-163。仮）、利用者の起票は「営業カレンダーが未登録」を返す。
 */
final class ServiceCaseIntake {

    /** 期限の計算で営業日カレンダーを読む期間の長さ（日）。登録のない日にかかれば登録漏れとして返す。 */
    private static final int CALENDAR_WINDOW_DAYS = 60;

    private static final ZoneId CALENDAR_ZONE = ZoneId.of("Asia/Tokyo");

    private final ServiceCaseRepository repository;
    private final ServiceCaseNumberIssuer numberIssuer;
    private final BusinessCalendarRepository calendars;
    private final Clock clock;
    private final ServiceDeadlinePolicy deadlinePolicy;

    ServiceCaseIntake(
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

    /**
     * 起票する。
     *
     * @param createdBy 起票者として記録する利用者
     * @param originRef 起票元キー（システムの起票だけ。利用者の起票は空）
     */
    OpenServiceCaseOutcome open(ServiceCaseDraft draft, UserId createdBy, Optional<String> originRef) {
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
            LocalDate from = today.minusDays(1);
            LocalDate to = today.plusDays(CALENDAR_WINDOW_DAYS);
            calendar = loadCalendar(from, to, originRef.isPresent());
            deadlines = deadlinePolicy.deadlinesFor(draft.priority(), now, calendar);
        } catch (BusinessCalendarNotRegisteredException _) {
            return new OpenServiceCaseOutcome.CalendarNotRegistered();
        }
        UtcInstant openedAt = new UtcInstant(now);
        CaseNumber number = numberIssuer.next(now.atZone(calendar.zone()).getYear());
        ServiceCase serviceCase =
                ((ServiceCaseOpening.Opened) ServiceCase.open(draft, number, deadlines, openedAt)).serviceCase();
        repository.save(serviceCase, createdBy);
        originRef.ifPresent(ref -> repository.recordOrigin(number, ref));
        return new OpenServiceCaseOutcome.Opened(
                number,
                serviceCase.receiveDueAt(),
                serviceCase.answerDueAt(),
                serviceCase.nextUpdateAt(),
                serviceCase.acknowledgedAt());
    }

    private BusinessCalendar loadCalendar(LocalDate from, LocalDate to, boolean system) {
        try {
            return calendars.load(from, to);
        } catch (BusinessCalendarNotRegisteredException e) {
            if (!system) {
                throw e;
            }
            return defaultCalendar(from, to);
        }
    }

    /** 既定のカレンダー（平日 9:00〜18:00 の日本時間、土日休み、祝日なし）。営業日カレンダーが未登録のときのシステムの起票だけが使う。 */
    private static BusinessCalendar defaultCalendar(LocalDate from, LocalDate to) {
        List<BusinessDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            days.add(
                    weekend
                            ? BusinessDay.closed(date, CALENDAR_ZONE, null)
                            : BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), CALENDAR_ZONE, null));
        }
        return BusinessCalendar.of(days);
    }
}
