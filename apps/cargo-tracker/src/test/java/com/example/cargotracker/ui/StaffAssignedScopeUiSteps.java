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
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
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
    private final String baseUrl;
    private String outOfScopeNumber;
    private TrackingNumber inScopeBooking;
    private TrackingNumber outOfScopeBooking;

    public StaffAssignedScopeUiSteps(
            BrowserSession browser,
            UiUsers users,
            TransportRequestCommandService transportRequests,
            BookingRepository bookings,
            BookingSagaRepository sagas,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.transportRequests = transportRequests;
        this.bookings = bookings;
        this.sagas = sagas;
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
