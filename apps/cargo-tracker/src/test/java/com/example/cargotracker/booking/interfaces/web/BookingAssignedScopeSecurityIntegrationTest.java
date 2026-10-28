package com.example.cargotracker.booking.interfaces.web;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 予約の側の社内の画面の担当範囲の統合テスト（Bolt 38f2。US-16 AC4、IA-INV-02・IA-INV-08、SEC-12、ADR-016・ADR-019）。本物のフィルターの
 * 連なりと PostgreSQL の上で、営業担当者の予約の 4 つの入口を担当企業で絞り、範囲外の GET は 404 で記録せず、範囲外の確定の POST は
 * 確定条件や予約済みによらず 404 で担当範囲の外の試行を 1 行残すことを確かめる。範囲の外では、見積りの理由（承認がまだなど）も予約済みの
 * 追跡番号も示さない（存在を漏らさない）。38f の {@code StaffAssignedScopeSecurityIntegrationTest} と同じ構成にし、Spring の文脈を増やさない
 * （#50）。共有の表は対象の業務番号と操作者で絞る（T-113）。業務番号はほかのテストとぶつからないよう 2088 年を使う。
 */
@IdentityIntegrationTest
class BookingAssignedScopeSecurityIntegrationTest {

    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String BOOKINGS_URL = "/staff/bookings";
    private static final String NO_ASSIGNED_COMPANIES = "担当企業がまだないので、表示できる予約はありません。システム管理者に担当企業の割り当てを頼んでください。";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);
    private static final Random RANDOM = new Random();
    /** テストの時刻（38f と同じ。置かないと TOTP の刻みが 0 になる）。見積りの有効期限と区間の出発は 2099 年。 */
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    QuotationRepository quotations;

    @Autowired
    BookingRepository bookings;

    @Autowired
    BookingSagaRepository sagas;

    private Company operator;
    private CompanyId shipperA;
    private CompanyId shipperB;
    /** 最後に作った営業担当者（監査の件数を操作者で絞る。T-113）。 */
    private UserId lastSales;

    @BeforeEach
    void 企業を用意する() {
        ((MutableClock) clock).setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        shipperA = new CompanyId(UUID.randomUUID());
        shipperB = new CompanyId(UUID.randomUUID());
    }

    @Test
    void 予約一覧には担当企業の予約だけが出る() throws Exception {
        // 行 1a・1b
        TrackingNumber inScope = booked(shipperA, nextNumber());
        TrackingNumber outOfScope = booked(shipperB, nextNumber());
        Cookie session = login(sales(shipperA));

        assertThat(body(session, BOOKINGS_URL)).contains(inScope.value()).doesNotContain(outOfScope.value());
    }

    @Test
    void 範囲の外の予約の詳細は404で記録しない範囲の中は開ける() throws Exception {
        // 行 2a・6a
        TrackingNumber inScope = booked(shipperA, nextNumber());
        TrackingNumber outOfScope = booked(shipperB, nextNumber());
        Cookie session = login(sales(shipperA));

        mvc.perform(get(BOOKINGS_URL + "/" + outOfScope.value()).cookie(session))
                .andExpect(status().isNotFound());
        assertThat(body(session, BOOKINGS_URL + "/" + inScope.value())).contains(inScope.value());
        assertThat(deniedOf(outOfScope.value())).isZero();
    }

    @Test
    void 範囲の外の見積りの予約の作成は404で記録しない範囲の中は開ける() throws Exception {
        // 行 2b・6b
        String inScope = readyToBook(shipperA, true);
        String outOfScope = readyToBook(shipperB, true);
        Cookie session = login(sales(shipperA));

        mvc.perform(newBooking(outOfScope).cookie(session)).andExpect(status().isNotFound());
        assertThat(body(session, BOOKINGS_URL + "/new?transportRequest=" + inScope + "&quotation=1"))
                .contains(inScope);
        assertThat(deniedOf(outOfScope + "/1")).isZero();
    }

    @Test
    void 範囲の外の見積りの確定は404で試行を1行記録する() throws Exception {
        // 行 3
        String outOfScope = readyToBook(shipperB, true);
        Cookie session = login(sales(shipperA));

        mvc.perform(confirm(outOfScope, UUID.randomUUID()).cookie(session).param("staffConfirmed", "true"))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope + "/1")).isOne();
        assertThat(bookingsOf(outOfScope)).isZero();
    }

    @Test
    void 範囲の外では承認がまだという理由も示さない() throws Exception {
        // 行 4（見積りの理由を漏らさない）
        String notApproved = readyToBook(shipperB, false);
        Cookie session = login(sales(shipperA));

        mvc.perform(newBooking(notApproved).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(confirm(notApproved, UUID.randomUUID()).cookie(session).param("staffConfirmed", "true"))
                .andExpect(status().isNotFound());
        assertThat(deniedOf(notApproved + "/1")).isOne();
    }

    @Test
    void 範囲の外の予約済みの見積りは追跡番号を示さず404で確定は記録する() throws Exception {
        // 行 4b（予約済みの判定より範囲の判定が先）
        String outOfScope = readyToBook(shipperB, true);
        TrackingNumber trackingNumber = booked(shipperB, outOfScope);
        Cookie session = login(sales(shipperA));

        mvc.perform(newBooking(outOfScope).cookie(session)).andExpect(status().isNotFound());
        String confirmed = mvc.perform(
                        confirm(outOfScope, UUID.randomUUID()).cookie(session).param("staffConfirmed", "true"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(confirmed).doesNotContain(trackingNumber.value());
        assertThat(deniedOf(outOfScope + "/1")).isOne();
    }

    @Test
    void 範囲の外で同じコマンドIDを再送しても決着済みの結果を示さず404で記録する() throws Exception {
        // 行 4c
        String outOfScope = readyToBook(shipperB, true);
        UUID commandId = UUID.randomUUID();
        Cookie other = login(sales(shipperB));
        mvc.perform(confirm(outOfScope, commandId).cookie(other).param("staffConfirmed", "true"))
                .andExpect(status().is3xxRedirection());
        Cookie session = login(sales(shipperA));

        mvc.perform(confirm(outOfScope, commandId).cookie(session).param("staffConfirmed", "true"))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope + "/1")).isOne();
    }

    @Test
    void 範囲の外で確認のチェックがなくても確定条件より先に404で記録する() throws Exception {
        // 行 5（T-211）
        String outOfScope = readyToBook(shipperB, true);
        Cookie session = login(sales(shipperA));

        mvc.perform(confirm(outOfScope, UUID.randomUUID()).cookie(session)).andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope + "/1")).isOne();
    }

    @Test
    void 型の誤りは範囲によらず400で記録しない() throws Exception {
        // 行 5b
        String outOfScope = readyToBook(shipperB, true);
        Cookie session = login(sales(shipperA));

        mvc.perform(confirm(outOfScope, UUID.randomUUID()).cookie(session).param("staffConfirmed", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BOOKINGS_URL)
                        .cookie(session)
                        .with(csrf())
                        .param("commandId", UUID.randomUUID().toString())
                        .param("transportRequest", outOfScope)
                        .param("quotation", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BOOKINGS_URL)
                        .cookie(session)
                        .with(csrf())
                        .param("commandId", "not-a-uuid")
                        .param("transportRequest", outOfScope)
                        .param("quotation", "1"))
                .andExpect(status().isBadRequest());

        assertThat(deniedOf(outOfScope + "/1")).isZero();
    }

    @Test
    void 範囲の中の見積りは確定できて予約の詳細へ移る() throws Exception {
        // 行 6c
        String inScope = readyToBook(shipperA, true);
        Cookie session = login(sales(shipperA));

        String location = mvc.perform(
                        confirm(inScope, UUID.randomUUID()).cookie(session).param("staffConfirmed", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        assertThat(location).startsWith(BOOKINGS_URL + "/CT");
        assertThat(deniedOf(inScope + "/1")).isZero();
    }

    @Test
    void 担当企業がない営業担当者は予約一覧が空で案内が出る() throws Exception {
        // 行 7
        TrackingNumber booking = booked(shipperA, nextNumber());
        Cookie session = login(sales());

        assertThat(body(session, BOOKINGS_URL))
                .doesNotContain(booking.value())
                .contains(NO_ASSIGNED_COMPANIES)
                .doesNotContain("確定した予約はまだありません");
        mvc.perform(get(BOOKINGS_URL + "/" + booking.value()).cookie(session)).andExpect(status().isNotFound());
    }

    @Test
    void 担当企業がない営業担当者は予約の作成が404で記録せず確定は404で記録する() throws Exception {
        // 行 7（一覧と詳細のほかの入口も閉じる）
        String quotation = readyToBook(shipperA, true);
        Cookie session = login(sales());

        mvc.perform(newBooking(quotation).cookie(session)).andExpect(status().isNotFound());
        assertThat(deniedOf(quotation + "/1")).isZero();
        mvc.perform(confirm(quotation, UUID.randomUUID()).cookie(session).param("staffConfirmed", "true"))
                .andExpect(status().isNotFound());
        assertThat(deniedOf(quotation + "/1")).isOne();
        assertThat(bookingsOf(quotation)).isZero();
    }

    private String sales(CompanyId... assigned) {
        String email = "booking-scope-test-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        lastSales = userId;
        users.add(User.of(
                userId,
                operator.id(),
                EmailAddress.of(email),
                "営業 一郎",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.SALES)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        for (CompanyId company : assigned) {
            AdministrationFixtures.assignCompany(jdbc, userId, company);
        }
        return email;
    }

    private Cookie login(String email) throws Exception {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock).login(email);
    }

    private static String nextNumber() {
        return new TransportRequestNumber(2088, SEQUENCE.getAndIncrement()).text();
    }

    /**
     * 審査・提示・詳細経路設計の依頼・経路の割当てまで進め、{@code approved} なら荷主の承認まで進めた予約待ちの輸送要求と見積り 1 を保存する
     * （前例は {@code BookingConfirmationWithRouteRedesignIntegrationTest}）。
     */
    private String readyToBook(CompanyId shipper, boolean approved) {
        UserId staff = new UserId(UUID.randomUUID());
        UserId shipperUser = new UserId(UUID.randomUUID());
        TransportRequestNumber number = new TransportRequestNumber(2088, SEQUENCE.getAndIncrement());
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                number,
                shipper,
                ShipmentTermsFixture.generalCargo(),
                staff,
                at("2026-11-05T01:00:00Z"));
        request.approve(1, staff, "契約条件を確認した", at("2026-11-05T03:00:00Z"));
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);
        if (approved) {
            request.markReadyToBook(1);
        }
        request.clearDomainEvents();
        transportRequests.save(request);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-11-05T04:00:00Z"));
        quotation.presentInternally(staff, at("2026-11-05T04:30:00Z"));
        quotation.requestRouteDesign(shipperUser, at("2026-11-06T02:00:00Z"));
        quotation.assignRoute(
                new AssignedRoute(
                        "RC-2088-%04d".formatted(number.sequence()),
                        1,
                        at("2026-11-07T05:00:00Z"),
                        List.of(new AssignedRouteLeg(
                                "V-201",
                                new Location("JPTYO"),
                                new Location("NLRTM"),
                                at("2099-10-10T00:00:00Z"),
                                at("2099-10-31T00:00:00Z")))),
                at("2026-11-07T05:00:00Z"));
        if (approved) {
            quotation.approveByShipper(shipperUser, at("2026-11-08T03:00:00Z"));
        }
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return number.text();
    }

    /** 荷主企業の確定した予約（見積り 1）と予約サガを、確定の画面を通さずに保存する（テストの準備）。 */
    private TrackingNumber booked(CompanyId shipper, String transportRequestNumber) {
        BookingTerms fixture = BookingFixture.terms();
        BookingTerms terms = new BookingTerms(
                UUID.randomUUID(),
                fixture.transportRequestVersionNo(),
                transportRequestNumber,
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
        UtcInstant committedAt = new UtcInstant(clock.instant());
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

    private static MockHttpServletRequestBuilder newBooking(String transportRequestNumber) {
        return get(BOOKINGS_URL + "/new")
                .param("transportRequest", transportRequestNumber)
                .param("quotation", "1");
    }

    private static MockHttpServletRequestBuilder confirm(String transportRequestNumber, UUID commandId) {
        return post(BOOKINGS_URL)
                .with(csrf())
                .param("commandId", commandId.toString())
                .param("transportRequest", transportRequestNumber)
                .param("quotation", "1");
    }

    private int deniedOf(String targetId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ASSIGNED_SCOPE_DENIED'"
                        + " AND target_type = 'QUOTATION' AND target_id = ? AND actor_user_id = ?",
                Integer.class,
                targetId,
                lastSales.value());
    }

    private int bookingsOf(String transportRequestNumber) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM booking.booking WHERE transport_request_number = ?",
                Integer.class,
                transportRequestNumber);
    }

    private String body(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
