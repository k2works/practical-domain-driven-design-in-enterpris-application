package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.application.internal.commands.CloseServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.RecordServiceCaseAnswerCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.OpenServiceCaseOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 有人案件の起票と終結の規則の業務ルール層のステップ定義（US-20 AC1・AC7・AC8。Bolt 40-16）。メモリ上のリポジトリと、平日 9:00〜18:00・土日休みの
 * 営業日カレンダーで、サービスを直接呼ぶ。状態はシナリオごとのこのクラスの値で持つ（共有のリポジトリを使わない）。
 */
public class ServiceCaseSteps {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final CompanyId COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040e1601"));

    private final MutableClock clock = new MutableClock();
    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();
    private final ServiceCaseCommandService commands =
            new ServiceCaseCommandService(repository, issuer, (from, to) -> calendar(), clock);
    private final ServiceCaseActionService actions = new ServiceCaseActionService(repository, clock);
    private final AuthenticatedActor support = new AuthenticatedActor(
            new UserId(UUID.fromString("00000000-0000-0000-0000-0000040e1602")),
            COMPANY,
            Set.of(Role.CUSTOMER_SUPPORT),
            "窓口",
            "A 社");

    private final List<OpenServiceCaseOutcome.Opened> opened = new ArrayList<>();
    private ServiceCaseActionOutcome closeOutcome;
    private CaseNumber outageNumber;

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

    @前提("有人案件の時計が {string} である")
    public void 時計を合わせる(String instant) {
        clock.setInstant(Instant.parse(instant));
    }

    @もし("顧客照会の有人案件を内容 {string} で起票する")
    public void 顧客照会を起票する(String content) {
        open(ServicePriority.NORMAL, content);
    }

    @もし("優先度高の顧客照会の有人案件を内容 {string} で起票する")
    public void 優先度高の顧客照会を起票する(String content) {
        open(ServicePriority.HIGH, content);
    }

    private void open(ServicePriority priority, String content) {
        ServiceCaseDraft draft = ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, priority, content, "CS")
                .withShipper(COMPANY);
        OpenServiceCaseOutcome outcome = commands.open(support, new OpenServiceCaseCommand(draft));
        assertThat(outcome).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
        opened.add((OpenServiceCaseOutcome.Opened) outcome);
    }

    @前提("外部停止の有人案件を起票して受領し回答を記録している")
    public void 外部停止を回答済みにする() {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.EXTERNAL_OUTAGE, ServicePriority.HIGH, "港湾システムの停止", "運用")
                .withManualSource("港湾からの電話連絡");
        OpenServiceCaseOutcome.Opened result =
                (OpenServiceCaseOutcome.Opened) commands.open(support, new OpenServiceCaseCommand(draft));
        outageNumber = result.number();
        commands.receive(support, new ReceiveServiceCaseCommand(outageNumber));
        ServiceCaseActionOutcome answered =
                actions.recordAnswer(support, new RecordServiceCaseAnswerCommand(outageNumber, "手動で代替しました"));
        assertThat(answered).isInstanceOf(ServiceCaseActionOutcome.Done.class);
    }

    @もし("有人案件を終結する")
    public void 終結する() {
        closeOutcome = actions.close(support, new CloseServiceCaseCommand(outageNumber));
    }

    @ならば("{int} 件の有人案件の受付番号は異なり、形は {string} で始まる")
    public void 受付番号は一意(int count, String prefix) {
        assertThat(opened).hasSize(count);
        assertThat(opened.stream().map(o -> o.number().value()).distinct().count())
                .isEqualTo(count);
        assertThat(opened).allSatisfy(o -> assertThat(o.number().value()).startsWith(prefix));
    }

    @ならば("終結は復旧照合が未完了で拒まれ、案件の状態は {string} のままである")
    public void 終結は拒まれる(String status) {
        assertThat(closeOutcome).isInstanceOf(ServiceCaseActionOutcome.ReconciliationIncomplete.class);
        assertThat(repository.cases.get(outageNumber.value()).status()).isEqualTo(ServiceCaseStatus.ANSWERED);
        assertThat(status).isEqualTo("回答済み");
    }

    @ならば("起票の結果に受付番号と次回更新日時があり、営業時間外のため起票の時刻に返している")
    public void 営業時間外の即時の返答() {
        OpenServiceCaseOutcome.Opened result = opened.getLast();
        assertThat(result.number().value()).startsWith("SC-");
        assertThat(result.nextUpdateAt().instant()).isAfter(clock.instant());
        assertThat(result.acknowledgedAt())
                .hasValueSatisfying(at -> assertThat(at.instant()).isEqualTo(clock.instant()));
    }
}
