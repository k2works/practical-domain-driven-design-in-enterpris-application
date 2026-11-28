package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 荷主担当者の追跡の照会の確認中と鮮度超過のお知らせ（US-09 AC2・AC3。Bolt 47）の画面の層のステップ定義。確認中は時系列に反する実績を登録した
 * 追跡記録、鮮度超過は次の予定の実績の予定時刻から許容を過ぎた追跡記録を、リポジトリへテストの準備として保存する。
 */
public class CustomerTrackingNoticeUiSteps {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Random RANDOM = new Random();

    private final BrowserSession browser;
    private final UiUsers users;
    private final TrackingRecordRepository trackingRecords;
    private final String baseUrl;

    private String trackingNumber;

    public CustomerTrackingNoticeUiSteps(
            BrowserSession browser,
            UiUsers users,
            TrackingRecordRepository trackingRecords,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.trackingRecords = trackingRecords;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    @前提("荷主の企業に時系列に反する実績で確認中になった追跡記録がある")
    public void 確認中の追跡記録がある() {
        Schedule future = new Schedule(
                "RC-UI-0047",
                1,
                List.of(TrackingFixture.leg("V900", "JPTYO", "KRPUS", "2099-01-01T00:00:00Z", "2099-01-03T00:00:00Z")));
        TrackingRecord started = start(future);
        Instant now = Instant.now();
        trackingRecords.save(started);
        // 保存は開始の状態だけを書くので、実績は読み直しては足して更新する（更新のたびに集約の版が進む）
        trackingRecords.update(register(started, MilestoneKind.PICKUP, now.minus(3, ChronoUnit.HOURS), "F-47-1"));
        TrackingRecord pickedUp =
                trackingRecords.findByTrackingNumber(started.trackingNumber()).orElseThrow();
        // 採用済みの集荷より前の出発は時系列に反するので、確認中になる
        trackingRecords.update(register(pickedUp, MilestoneKind.DEPARTURE, now.minus(5, ChronoUnit.HOURS), "F-47-2"));
    }

    @前提("荷主の企業に次の予定の実績の予定時刻から許容を過ぎた追跡記録がある")
    public void 鮮度超過の追跡記録がある() {
        trackingRecords.save(start(TrackingFixture.schedule()));
    }

    @もし("荷主担当者が準備した追跡記録の照会の結果を開く")
    public void 照会の結果を開く() {
        browser.navigate(baseUrl + "/customer/tracking-records/" + trackingNumber);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡の照会 " + trackingNumber)))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("確認中のお知らせに判明していること・未確定のこと・次の行動・次回の更新・担当窓口が示される")
    public void 確認中のお知らせが示される() {
        var notice = page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("確認中のお知らせ"));
        assertThat(notice).isVisible();
        for (String term : List.of("判明していること", "未確定のこと", "次の行動", "次回の更新", "担当窓口")) {
            assertThat(notice).containsText(term);
        }
        assertThat(notice).containsText("平日 09:00〜18:00 Asia/Tokyo（UTC+09:00）");
    }

    @ならば("鮮度超過のお知らせに最新性を保証できないことと最後の採用値と次回の更新が示される")
    public void 鮮度超過のお知らせが示される() {
        var notice = page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("最新性についてのお知らせ"));
        assertThat(notice).isVisible();
        assertThat(notice).containsText("最新性を保証できません");
        assertThat(notice).containsText("最後に採用した状態は「集荷予定」です");
        assertThat(notice).containsText("次回の更新");
        assertThat(notice).containsText("担当窓口");
    }

    @ならば("確認中のお知らせは示されない")
    public void 確認中のお知らせは示されない() {
        assertThat(page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("確認中のお知らせ")))
                .hasCount(0);
    }

    private TrackingRecord start(Schedule schedule) {
        CompanyId shipper = users.companyOf(Role.SHIPPER);
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        TrackingRecord started = TrackingRecord.start(
                        new TrackingNumber(value.toString()),
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        schedule,
                        new UtcInstant(Instant.now().minus(1, ChronoUnit.DAYS)))
                .trackingRecord();
        trackingNumber = started.trackingNumber().value();
        return started;
    }

    private TrackingRecord register(TrackingRecord target, MilestoneKind kind, Instant occurredAt, String reference) {
        UtcInstant registeredAt = new UtcInstant(Instant.now().minus(1, ChronoUnit.HOURS));
        return target.registerMilestone(
                        kind,
                        new Location("JPTYO"),
                        new UtcInstant(occurredAt),
                        new Source(SourceKind.FIELD_RECORD, reference, registeredAt),
                        users.of(Role.SHIPPER).userId(),
                        registeredAt)
                .trackingRecord();
    }
}
