package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 営業担当者の担当範囲（Bolt 38f。US-16 AC4）の画面の層のステップ定義。担当企業の外の見積依頼は、テストの準備としてサービスで
 * 提出する（範囲の外の荷主の利用者は作らない）。範囲の外は一覧に出ず、URL を開くと存在しない見積依頼と同じく画面が見つからない。
 * 予約の側（Bolt 38f2）の範囲の外の予約は、荷主 B の承認と確定まで画面で進めるのが重いので、テストの準備として予約と予約サガを直接保存する。
 */
public class StaffAssignedScopeUiSteps {

    private final BrowserSession browser;
    private final UiUsers users;
    private static final Random RANDOM = new Random();
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private final TransportRequestCommandService transportRequests;
    private final BookingRepository bookings;
    private final BookingSagaRepository sagas;
    private final RoutingCaseRepository routingCases;
    private final RoutingCaseNumberIssuer routingCaseNumbers;
    private final TrackingRecordRepository trackingRecords;
    private final String baseUrl;
    private String outOfScopeNumber;
    private TrackingNumber inScopeBooking;
    private TrackingNumber outOfScopeBooking;
    private String inScopeCase;
    private String outOfScopeCase;
    private String inScopeRecord;
    private String outOfScopeRecord;

    @SuppressWarnings("java:S107") // シナリオの準備に使う部品と画面の URL をすべて受け取る
    public StaffAssignedScopeUiSteps(
            BrowserSession browser,
            UiUsers users,
            TransportRequestCommandService transportRequests,
            BookingRepository bookings,
            BookingSagaRepository sagas,
            RoutingCaseRepository routingCases,
            RoutingCaseNumberIssuer routingCaseNumbers,
            TrackingRecordRepository trackingRecords,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.transportRequests = transportRequests;
        this.bookings = bookings;
        this.sagas = sagas;
        this.routingCases = routingCases;
        this.routingCaseNumbers = routingCaseNumbers;
        this.trackingRecords = trackingRecords;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    @前提("担当企業の外の荷主が見積依頼を提出している")
    public void 担当企業の外の荷主が提出している() {
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) transportRequests.submit(new SubmitTransportRequestCommand(
                        users.outOfScopeShipper(),
                        new UserId(UUID.randomUUID()),
                        ShipmentTermsFixture.completeInput()));
        outOfScopeNumber = submitted.number().text();
    }

    @ならば("受付一覧に担当企業の外の見積依頼が表示されない")
    public void 受付一覧に範囲の外が表示されない() {
        assertThat(page().locator("main")).not().containsText(outOfScopeNumber);
    }

    @もし("営業担当者が担当企業の外の見積依頼の審査画面の URL を開く")
    public void 範囲の外の審査画面を開く() {
        browser.navigate(baseUrl + "/staff/transport-requests/" + outOfScopeNumber);
    }

    @ならば("存在しない見積依頼の URL を開いたときと同じく画面が見つからないと示される")
    public void 存在しない見積依頼と同じく見つからない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).not().containsText(outOfScopeNumber);
        // 行 16b: /error のディスパッチでも主体の担当企業が null で 500 にならず、共通の 404 が出る（認証の前のレイアウト。表示名は出さない）
        assertThat(page().locator("main")).containsText("お探しの画面は見つかりません。");
        browser.checkAccessibility();
        String outOfScope = page().locator("main").innerText();
        browser.navigate(baseUrl + "/staff/transport-requests/TR-2099-9999");
        assertThat(page().locator("main")).hasText(outOfScope);
    }

    @前提("担当企業の荷主と担当企業の外の荷主の予約がある")
    public void 担当企業と担当企業の外の予約がある() {
        inScopeBooking = booked(users.companyOf(Role.SHIPPER));
        outOfScopeBooking = booked(users.outOfScopeShipper());
    }

    @もし("営業担当者が社内の予約一覧を表示する")
    public void 社内の予約一覧を開く() {
        browser.navigate(baseUrl + "/staff/bookings");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("予約一覧");
        browser.checkAccessibility();
    }

    @ならば("予約一覧に担当企業の予約が表示され担当企業の外の予約が表示されない")
    public void 予約一覧に担当企業の予約だけが表示される() {
        assertThat(page().locator("main")).containsText(inScopeBooking.value());
        assertThat(page().locator("main")).not().containsText(outOfScopeBooking.value());
    }

    @もし("営業担当者が担当企業の外の予約の詳細の URL を開く")
    public void 範囲の外の予約の詳細を開く() {
        browser.navigate(baseUrl + "/staff/bookings/" + outOfScopeBooking.value());
    }

    @ならば("存在しない予約の URL を開いたときと同じく画面が見つからないと示される")
    public void 存在しない予約と同じく見つからない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).not().containsText(outOfScopeBooking.value());
        browser.checkAccessibility();
        String outOfScope = page().locator("main").innerText();
        browser.navigate(baseUrl + "/staff/bookings/CTZZZZZZZZZZZZ");
        assertThat(page().locator("main")).hasText(outOfScope);
    }

    @前提("担当企業の荷主と担当企業の外の荷主の経路設計の案件がある")
    public void 担当企業と担当企業の外の経路設計の案件がある() {
        inScopeCase = opened(users.companyOf(Role.SHIPPER));
        outOfScopeCase = opened(users.outOfScopeShipper());
    }

    @もし("経路設計者が社内の経路設計の案件一覧を表示する")
    public void 社内の経路設計の案件一覧を開く() {
        browser.navigate(baseUrl + "/staff/routing-cases");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("経路設計案件一覧");
        browser.checkAccessibility();
    }

    @ならば("案件一覧に担当企業の案件が表示され担当企業の外の案件が表示されない")
    public void 案件一覧に担当企業の案件だけが表示される() {
        assertThat(page().locator("main")).containsText(inScopeCase);
        assertThat(page().locator("main")).not().containsText(outOfScopeCase);
    }

    @もし("経路設計者が担当企業の外の案件の URL を開く")
    public void 範囲の外の案件を開く() {
        browser.navigate(baseUrl + "/staff/routing-cases/" + outOfScopeCase);
    }

    @ならば("存在しない案件の URL を開いたときと同じく画面が見つからないと示される")
    public void 存在しない案件と同じく見つからない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).not().containsText(outOfScopeCase);
        browser.checkAccessibility();
        String outOfScope = page().locator("main").innerText();
        browser.navigate(baseUrl + "/staff/routing-cases/RC-2099-9999");
        assertThat(page().locator("main")).hasText(outOfScope);
    }

    @前提("担当企業の荷主と担当企業の外の荷主の追跡記録がある")
    public void 担当企業と担当企業の外の追跡記録がある() {
        inScopeRecord = started(users.companyOf(Role.SHIPPER));
        outOfScopeRecord = started(users.outOfScopeShipper());
    }

    @もし("追跡管理者が社内の追跡一覧を表示する")
    public void 社内の追跡一覧を開く() {
        browser.navigate(baseUrl + "/staff/tracking-records");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("追跡一覧");
        browser.checkAccessibility();
    }

    @ならば("追跡一覧に担当企業の追跡記録が表示され担当企業の外の追跡記録が表示されない")
    public void 追跡一覧に担当企業の追跡記録だけが表示される() {
        assertThat(page().locator("main")).containsText(inScopeRecord);
        assertThat(page().locator("main")).not().containsText(outOfScopeRecord);
    }

    @もし("追跡管理者が担当企業の外の追跡記録の URL を開く")
    public void 範囲の外の追跡記録を開く() {
        browser.navigate(baseUrl + "/staff/tracking-records/" + outOfScopeRecord);
    }

    @ならば("存在しない追跡記録の URL を開いたときと同じく画面が見つからないと示される")
    public void 存在しない追跡記録と同じく見つからない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).not().containsText(outOfScopeRecord);
        browser.checkAccessibility();
        String outOfScope = page().locator("main").innerText();
        browser.navigate(baseUrl + "/staff/tracking-records/CTZZZZZZZZZZZZ");
        assertThat(page().locator("main")).hasText(outOfScope);
    }

    /** 荷主企業の追跡を開始した追跡記録を、予約の確定と DE-07 の処理を通さずに保存する（テストの準備。Bolt 38g）。 */
    private String started(CompanyId shipper) {
        TrackingRecord trackingRecord = TrackingRecord.start(
                        new com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber(
                                TrackingNumber.generate(RANDOM).value()),
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        new UtcInstant(Instant.parse("2099-01-01T00:00:00Z")))
                .trackingRecord();
        trackingRecords.save(trackingRecord);
        return trackingRecord.trackingNumber().value();
    }

    /** 荷主企業の経路設計案件（経路版 1 は作成中）を、DE-16 の処理を通さずに保存する（テストの準備。Bolt 38g）。 */
    private String opened(CompanyId shipper) {
        RoutingCase routingCase = RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                routingCaseNumbers.next(2086),
                UUID.randomUUID(),
                "TR-2086-%04d".formatted(SEQUENCE.getAndIncrement()),
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        new UtcInstant(Instant.parse("2099-10-31T00:00:00Z")),
                        "GENERAL"),
                new UtcInstant(Instant.now()),
                new UtcInstant(Instant.parse("2099-11-30T00:00:00Z")),
                UUID.randomUUID(),
                shipper);
        routingCases.save(routingCase);
        return routingCase.number().text();
    }

    /** 荷主企業の確定した予約（見積り 1）と予約サガを、確定の画面を通さずに保存する（テストの準備）。 */
    private TrackingNumber booked(CompanyId shipper) {
        BookingTerms fixture = BookingFixture.terms();
        BookingTerms terms = new BookingTerms(
                UUID.randomUUID(),
                fixture.transportRequestVersionNo(),
                "TR-2096-%04d".formatted(SEQUENCE.getAndIncrement()),
                UUID.randomUUID(),
                1,
                shipper,
                fixture.consigneeCompanyId(),
                fixture.routingCaseNumber(),
                fixture.routeVersionNo(),
                fixture.cargoCategory(),
                fixture.cargoSummary(),
                fixture.shipperApproverId());
        TrackingNumber trackingNumber = TrackingNumber.generate(RANDOM);
        UtcInstant committedAt = new UtcInstant(Instant.now());
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms,
                        trackingNumber,
                        BookingFixture.SALES,
                        committedAt)
                .booking();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagas.save(BookingSaga.start(booking.id(), trackingNumber, committedAt));
        return trackingNumber;
    }
}
