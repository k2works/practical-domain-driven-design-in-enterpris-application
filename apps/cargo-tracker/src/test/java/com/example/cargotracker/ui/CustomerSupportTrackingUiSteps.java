package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.ExternalMilestoneReceipt;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneRegistration;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * カスタマーサポートの S-12 追跡の詳細の照会のみ（US-11 AC1・AC3。Bolt 40-10・40-16）の画面の層のステップ定義。追跡記録は、予約の確定の流れを
 * 通さずに、カスタマーサポートの担当企業のものをテストの準備として保存する（採用済みの集荷 1 件と、外部原本の出発の下書き 1 件）。
 */
public class CustomerSupportTrackingUiSteps {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Random RANDOM = new Random();

    private final BrowserSession browser;
    private final UiUsers users;
    private final TrackingRecordRepository trackingRecords;
    private final String baseUrl;

    private String trackingNumber;
    private Response lastResponse;

    public CustomerSupportTrackingUiSteps(
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

    @前提("荷主の企業の追跡記録に採用済みの実績と下書きの実績がある")
    public void 採用済みと下書きの実績がある() {
        CompanyId shipper = users.companyOf(Role.SHIPPER);
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        TrackingNumber number = new TrackingNumber(value.toString());
        Instant now = Instant.now();
        TrackingRecord started = TrackingRecord.start(
                        number,
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        new UtcInstant(now.minus(6, ChronoUnit.HOURS)))
                .trackingRecord();
        UserId registrant = users.of(Role.TRACKING_MANAGER).userId();
        // 保存は追跡記録の新規の行（予定区間まで）。実績は、保存した行を読み直して足し、更新する（版は読み直した値から進める）
        trackingRecords.save(started);
        MilestoneRegistration adopted = trackingRecords
                .findByTrackingNumber(number)
                .orElseThrow()
                .registerMilestone(
                        MilestoneKind.PICKUP,
                        new Location("JPTYO"),
                        new UtcInstant(now.minus(3, ChronoUnit.HOURS)),
                        new Source(SourceKind.FIELD_RECORD, "F-118", new UtcInstant(now.minus(2, ChronoUnit.HOURS))),
                        registrant,
                        new UtcInstant(now.minus(2, ChronoUnit.HOURS)));
        ExternalMilestone external = new ExternalMilestone(
                number,
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                new UtcInstant(now.minus(1, ChronoUnit.HOURS)),
                new Source(
                        SourceKind.EXTERNAL_RECORD,
                        UUID.randomUUID().toString(),
                        new UtcInstant(now.minus(30, ChronoUnit.MINUTES))),
                new ExternalOrigin(UUID.randomUUID(), "MS-0001", new UtcInstant(now.minus(20, ChronoUnit.MINUTES))),
                registrant);
        trackingRecords.update(adopted.trackingRecord());
        ExternalMilestoneReceipt receipt = trackingRecords
                .findByTrackingNumber(number)
                .orElseThrow()
                .receiveExternalMilestone(external, new UtcInstant(now));
        trackingRecords.update(((ExternalMilestoneReceipt.Received) receipt).trackingRecord());
        trackingNumber = number.value();
    }

    @もし("カスタマーサポートが追跡の詳細を開く")
    public void 追跡の詳細を開く() {
        signIn();
        page().navigate(baseUrl + "/staff/tracking-records/" + trackingNumber);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡の詳細 " + trackingNumber)))
                .isVisible();
        browser.checkAccessibility();
    }

    @もし("カスタマーサポートが実績の登録の画面を直接開く")
    public void 登録の画面を直接開く() {
        lastResponse = page().navigate(baseUrl + "/staff/tracking-records/" + trackingNumber + "/milestones/new");
    }

    @ならば("実績 {int} の顧客への表示は {string} と示される")
    public void 顧客への表示(int milestoneNo, String display) {
        assertThat(page().locator("#milestone-" + milestoneNo)).containsText("顧客への表示 " + display);
    }

    @ならば("追跡の詳細に {string} が示される")
    public void 追跡の詳細に示される(String text) {
        assertThat(page().getByText(text)).isVisible();
    }

    @ならば("追跡の詳細に実績の登録のリンクと採用のボタンは示されない")
    public void 登録と採用の操作は示されない() {
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("実績を登録")))
                .hasCount(0);
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("を採用する"))))
                .hasCount(0);
    }

    @ならば("アクセスは拒まれる")
    public void アクセスは拒まれる() {
        org.assertj.core.api.Assertions.assertThat(lastResponse.status()).isEqualTo(403);
    }

    /** カスタマーサポートでログインし直す（URL から役割を決める部品は追跡の画面を追跡管理者にするため、役割を指定して入る）。 */
    private void signIn() {
        browser.signInAs(Role.CUSTOMER_SUPPORT, baseUrl + "/login");
    }

    private Page page() {
        return browser.page();
    }
}
