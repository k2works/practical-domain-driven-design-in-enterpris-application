package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** システムの有人案件の起票（起票元キーの冪等と、営業カレンダー未登録のときの既定のカレンダー。Bolt 40-17・40-18。W8 の週全体レビュー A・B）。 */
class SystemServiceCaseOpenerTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    /** 月 2026-11-16 10:00 JST（営業時間内）。 */
    private static final Instant MONDAY_10 = Instant.parse("2026-11-16T01:00:00Z");

    private static final UserId SYSTEM_USER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040c7099"));

    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();

    private static BusinessCalendar calendar() {
        List<BusinessDay> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 11, 1);
                !date.isAfter(LocalDate.of(2027, 1, 31));
                date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            days.add(
                    weekend
                            ? BusinessDay.closed(date, JST, null)
                            : BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null));
        }
        return BusinessCalendar.of(days);
    }

    private SystemServiceCaseOpener opener() {
        return new SystemServiceCaseOpener(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(MONDAY_10, ZoneOffset.UTC));
    }

    private static ServiceCaseDraft outage() {
        return ServiceCaseDraft.of(ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "外部停止", "運用")
                .withManualSource("manual-1");
    }

    @Test
    void 行1_起票元キーが同じなら内容が違っても新しく起票せず既存の受付番号を返す() {
        SystemServiceCaseOpener opener = opener();

        OpenServiceCaseOutcome first = opener.open(outage(), SYSTEM_USER, "ORIGIN-1");
        OpenServiceCaseOutcome second = opener.open(
                ServiceCaseDraft.of(ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "別の文言", "運用")
                        .withManualSource("manual-2"),
                SYSTEM_USER,
                "ORIGIN-1");

        CaseNumber number = ((OpenServiceCaseOutcome.Opened) first).number();
        assertThat(second).isEqualTo(new OpenServiceCaseOutcome.Duplicate(number));
        assertThat(repository.cases).hasSize(1);
    }

    @Test
    void 行2_起票元キーが同じなら案件を終結した後の再配信でも新しく起票しない() {
        SystemServiceCaseOpener opener = opener();
        CaseNumber number = ((OpenServiceCaseOutcome.Opened) opener.open(outage(), SYSTEM_USER, "ORIGIN-2")).number();
        repository.markDuplicateClosed(number);

        OpenServiceCaseOutcome again = opener.open(outage(), SYSTEM_USER, "ORIGIN-2");

        assertThat(again).isEqualTo(new OpenServiceCaseOutcome.Duplicate(number));
        assertThat(repository.cases).hasSize(1);
    }

    @Test
    void 行3_起票元キーが違えば別の案件として起票する() {
        SystemServiceCaseOpener opener = opener();
        opener.open(outage(), SYSTEM_USER, "ORIGIN-3");
        repository.cases.values().forEach(c -> repository.markDuplicateClosed(c.number()));

        OpenServiceCaseOutcome other = opener.open(outage(), SYSTEM_USER, "ORIGIN-4");

        assertThat(other).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
        assertThat(repository.cases).hasSize(2);
    }

    @Test
    void 行4_営業カレンダーが未登録でも既定のカレンダーで期限を計算して起票する() {
        SystemServiceCaseOpener noCalendar = new SystemServiceCaseOpener(
                repository,
                issuer,
                (from, to) -> {
                    throw new BusinessCalendarNotRegisteredException(from);
                },
                Clock.fixed(MONDAY_10, ZoneOffset.UTC));

        OpenServiceCaseOutcome outcome = noCalendar.open(outage(), SYSTEM_USER, "ORIGIN-5");

        // 既定のカレンダー（平日 9:00〜18:00、土日休み、祝日なし）。月曜 10:00 の優先度高は営業時間内なので即時の返答は要らない
        assertThat(outcome).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
        OpenServiceCaseOutcome.Opened opened = (OpenServiceCaseOutcome.Opened) outcome;
        assertThat(opened.receiveDueAt().instant()).isAfter(MONDAY_10);
        assertThat(opened.acknowledgedAt()).isEmpty();
    }
}
