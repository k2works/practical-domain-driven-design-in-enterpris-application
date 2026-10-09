package com.example.cargotracker.booking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.internal.queryservices.BookingConfirmationPage;
import com.example.cargotracker.booking.application.internal.queryservices.BookingDetail;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.RecentBookings;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S-09 本予約の確定と S-24 予約の詳細（US-04 AC1・AC2・AC4。Bolt 23b・24）。S-09 は画面そのものが確認の領域（BR-13）。開いたときの
 * 判定は参考で、確定の可否は送ったときの照会（commit 時刻）で決まる（ADR-016）。URL に内部の ID を出さない（D-4）。開くたびに
 * コマンド ID を発行し、同じコマンド ID の再送には最初の結果を返す（B-INV-03）。
 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SALES)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = BookingController.class)
class BookingControllerTest {

    private static final String NEW = "/staff/bookings/new?transportRequest=TR-2026-0001&quotation=1";
    private static final String RECEPTION = "/staff/transport-requests";
    private static final String COMMAND_ID = "00000000-0000-0000-0000-0000000000c1";
    private static final String ALREADY_BOOKED = "TR-2026-0001 見積 1 はすでに予約に使われています（追跡番号 CTABCDEFGH2345）。";
    private static final UtcInstant APPROVED_AT = new UtcInstant(Instant.parse("2026-10-04T06:20:00Z"));
    private static final UtcInstant EXPIRES_AT = new UtcInstant(Instant.parse("2099-10-08T09:00:00Z"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookingCommandService commandService;

    @MockitoBean
    BookingQueryService queryService;

    private static BookingConfirmationPage.Available available(BookingTerms terms) {
        return new BookingConfirmationPage.Available(terms, APPROVED_AT, EXPIRES_AT);
    }

    // S-09 本予約の確定

    @Test
    void 確定の画面に確定条件と開いた時刻での確認であることと確認の領域を示す() throws Exception {
        given(queryService.confirmation("TR-2026-0001", 1)).willReturn(available(BookingFixture.terms()));

        mockMvc.perform(get(NEW))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("本予約の確定 TR-2026-0001 見積 1")))
                .andExpect(content()
                        .string(containsString(
                                "承認済み・有効期限 2099-10-08 18:00 Asia/Tokyo（UTC+09:00）（UTC 2099-10-08 09:00）")))
                .andExpect(content().string(containsString("一般貨物 パレット 10 個 1,200 kg")))
                .andExpect(content()
                        .string(containsString("2026-10-04 15:20 Asia/Tokyo（UTC+09:00）（UTC 2026-10-04 06:20）")))
                .andExpect(content().string(containsString("RC-2026-0001 版 1")))
                .andExpect(content().string(containsString("この表示は開いた時刻での確認です。有効期限の判定は確定の時刻で行い、期限と同時刻以後は確定できません。")))
                .andExpect(content().string(containsString("本予約を確定しますか")))
                .andExpect(content().string(containsString("本予約を確定し、追跡番号を発行します")))
                .andExpect(content().string(containsString("集荷前なら変更・取消しを申請できます（申請の画面は準備中です）")))
                .andExpect(content().string(containsString("name=\"staffConfirmed\"")))
                .andExpect(content().string(containsString("本予約を確定する")))
                .andExpect(content().string(not(containsString(BookingFixture.QUOTATION.toString()))))
                .andExpect(content().string(not(containsString(BookingFixture.TRANSPORT_REQUEST.toString()))));
    }

    @Test
    void 貨物の要約がない見積りは必須貨物情報が欠けていると示す() throws Exception {
        BookingTerms base = BookingFixture.terms();
        BookingTerms noCargo = new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                base.transportRequestNumber(),
                base.quotationId(),
                base.quotationNo(),
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                " ",
                base.shipperApproverId());
        given(queryService.confirmation("TR-2026-0001", 1)).willReturn(available(noCargo));

        mockMvc.perform(get(NEW)).andExpect(status().isOk()).andExpect(content().string(containsString("貨物の要約がありません")));
    }

    @Test
    void 確定に使えない見積りと確定済みの見積りは開くと受付一覧に戻して理由を示す() throws Exception {
        String subject = "TR-2026-0001 見積 1";
        for (var entry : List.of(
                new Object[] {QuotationUnavailability.EXPIRED, subject + " は有効期限を過ぎたため本予約を確定できません。再見積りが必要です。"},
                new Object[] {
                    QuotationUnavailability.REPLACED, subject + " は新しい見積りに置き換えられたため本予約を確定できません。最新の見積りを確かめてください。"
                },
                new Object[] {QuotationUnavailability.NOT_APPROVED, subject + " は荷主の承認がまだのため本予約を確定できません。"})) {
            given(queryService.confirmation("TR-2026-0001", 1))
                    .willReturn(new BookingConfirmationPage.Unavailable((QuotationUnavailability) entry[0]));

            mockMvc.perform(get(NEW))
                    .andExpect(redirectedUrl(RECEPTION))
                    .andExpect(flash().attribute("problem", entry[1]));
        }
        given(queryService.confirmation("TR-2026-0001", 1))
                .willReturn(new BookingConfirmationPage.AlreadyBooked(BookingFixture.TRACKING_NUMBER));

        mockMvc.perform(get(NEW))
                .andExpect(redirectedUrl(RECEPTION))
                .andExpect(flash().attribute("result", ALREADY_BOOKED))
                .andExpect(flash().attribute("resultLinkHref", "/staff/bookings/CTABCDEFGH2345"))
                .andExpect(flash().attribute("resultLinkLabel", "CTABCDEFGH2345 の予約の詳細を開く"));
    }

    @Test
    void 見つからない見積りは404にする() throws Exception {
        given(queryService.confirmation("TR-2026-0001", 1))
                .willReturn(new BookingConfirmationPage.Unavailable(QuotationUnavailability.NOT_FOUND));

        mockMvc.perform(get(NEW)).andExpect(status().isNotFound());
    }

    @Test
    void 確定すると予約の詳細へ移り追跡番号を示し追跡の開始の状態はお知らせに書かない() throws Exception {
        given(commandService.confirm(any()))
                .willReturn(new BookingConfirmationOutcome.Confirmed(BookingFixture.TRACKING_NUMBER));

        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(redirectedUrl("/staff/bookings/CTABCDEFGH2345"))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 で本予約を確定しました。追跡番号は CTABCDEFGH2345 です。"));

        ArgumentCaptor<ConfirmBookingCommand> command = ArgumentCaptor.forClass(ConfirmBookingCommand.class);
        verify(commandService).confirm(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue())
                .extracting(
                        ConfirmBookingCommand::transportRequestNumber,
                        ConfirmBookingCommand::quotationNo,
                        ConfirmBookingCommand::staffConfirmed)
                .containsExactly("TR-2026-0001", 1, true);
        org.assertj.core.api.Assertions.assertThat(command.getValue().operator().userId())
                .isEqualTo(TestActors.STAFF_USER);
        org.assertj.core.api.Assertions.assertThat(command.getValue().commandId())
                .isEqualTo(new CommandId(UUID.fromString(COMMAND_ID)));
    }

    @Test
    void 確定の画面は開くたびに新しいコマンドIDを隠し項目に入れる() throws Exception {
        given(queryService.confirmation("TR-2026-0001", 1)).willReturn(available(BookingFixture.terms()));

        String first = mockMvc.perform(get(NEW)).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get(NEW)).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(hiddenCommandId(first))
                .isNotNull()
                .isNotEqualTo(hiddenCommandId(second));
    }

    @Test
    void 確定条件が欠けて確定の画面に戻るときは送ったコマンドIDを隠し項目に残す() throws Exception {
        given(commandService.confirm(any()))
                .willReturn(
                        new BookingConfirmationOutcome.MissingConditions(List.of(BookingCondition.STAFF_CONFIRMATION)));
        given(queryService.confirmation("TR-2026-0001", 1)).willReturn(available(BookingFixture.terms()));

        String body = mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(hiddenCommandId(body)).isEqualTo(COMMAND_ID);
    }

    @Test
    void 同じコマンドIDで内容が違う確定は受付一覧に戻して開き直しを促す() throws Exception {
        given(commandService.confirm(any())).willReturn(new BookingConfirmationOutcome.CommandConflict());

        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(redirectedUrl(RECEPTION))
                .andExpect(flash().attribute(
                                "problem", "TR-2026-0001 見積 1 は確定していません。この操作はすでに別の内容で受け付けています。受付一覧から開き直してください。"));
    }

    @Test
    void コマンドIDが欠けているか形でない送信は400にして確定しない() throws Exception {
        mockMvc.perform(post("/staff/bookings")
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", "not-a-uuid")
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(status().isBadRequest());

        verify(commandService, never()).confirm(any());
    }

    private static String hiddenCommandId(String html) {
        Matcher matcher =
                Pattern.compile("name=\"commandId\" value=\"([^\"]+)\"").matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Test
    void 契約条件と照合したことを示さずに送ると確定せずにエラー要約で示す() throws Exception {
        given(commandService.confirm(any()))
                .willReturn(
                        new BookingConfirmationOutcome.MissingConditions(List.of(BookingCondition.STAFF_CONFIRMATION)));
        given(queryService.confirmation("TR-2026-0001", 1)).willReturn(available(BookingFixture.terms()));

        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("本予約を確定できませんでした")))
                .andExpect(content().string(containsString("href=\"#staffConfirmed\"")))
                .andExpect(content().string(containsString("営業担当者の確認: 契約条件と照合したことを確かめて、チェックを入れてください。")))
                .andExpect(content().string(containsString("aria-describedby=\"staffConfirmed-error\"")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
    }

    @Test
    void 貨物の要約がないまま送ると確定せずにエラー要約で必須貨物情報が欠けていると示す() throws Exception {
        BookingTerms base = BookingFixture.terms();
        BookingTerms noCargo = new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                base.transportRequestNumber(),
                base.quotationId(),
                base.quotationNo(),
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                " ",
                base.shipperApproverId());
        given(commandService.confirm(any()))
                .willReturn(new BookingConfirmationOutcome.MissingConditions(List.of(BookingCondition.REQUIRED_CARGO)));
        given(queryService.confirmation("TR-2026-0001", 1)).willReturn(available(noCargo));

        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("本予約を確定できませんでした")))
                .andExpect(content().string(containsString("必須貨物情報: 貨物の要約がありません。見積依頼の貨物を確かめてください。")))
                .andExpect(content().string(not(containsString("営業担当者の確認: 契約条件と照合したことを"))));
    }

    @Test
    void 送ったときに確定に使えない見積りと確定済みは受付一覧に戻して理由を示す() throws Exception {
        given(commandService.confirm(any())).willReturn(new BookingConfirmationOutcome.Expired());

        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(redirectedUrl(RECEPTION))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は有効期限を過ぎたため本予約を確定できません。再見積りが必要です。"));

        given(commandService.confirm(any()))
                .willReturn(new BookingConfirmationOutcome.AlreadyBooked(BookingFixture.TRACKING_NUMBER));

        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true"))
                .andExpect(redirectedUrl(RECEPTION))
                .andExpect(flash().attribute("result", ALREADY_BOOKED))
                .andExpect(flash().attribute("resultLinkHref", "/staff/bookings/CTABCDEFGH2345"))
                .andExpect(flash().attribute("resultLinkLabel", "CTABCDEFGH2345 の予約の詳細を開く"));
    }

    @Test
    void 業務番号の形でない見積りは見積りの公開APIが見つからないと返すので開いても送っても404にする() throws Exception {
        given(queryService.confirmation("XX", 1))
                .willReturn(new BookingConfirmationPage.Unavailable(QuotationUnavailability.NOT_FOUND));
        given(commandService.confirm(any()))
                .willReturn(new BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability.NOT_FOUND));

        mockMvc.perform(get("/staff/bookings/new?transportRequest=XX&quotation=1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/staff/bookings")
                        .param("commandId", COMMAND_ID)
                        .param("transportRequest", "XX")
                        .param("quotation", "1"))
                .andExpect(status().isNotFound());
    }

    // S-24 予約の詳細

    @Test
    void 予約の詳細に追跡番号と予約版と追跡の開始が処理中であることを示し完了とは示さない() throws Exception {
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        BookingFixture.terms(),
                        BookingFixture.TRACKING_NUMBER,
                        BookingFixture.SALES,
                        new UtcInstant(Instant.parse("2026-10-08T00:30:00Z")))
                .booking();
        given(queryService.detail(BookingFixture.TRACKING_NUMBER))
                .willReturn(Optional.of(new BookingDetail(booking, BookingSagaStatus.IN_PROGRESS)));

        mockMvc.perform(get("/staff/bookings/CTABCDEFGH2345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("予約の詳細 CTABCDEFGH2345")))
                .andExpect(content().string(containsString("TR-2026-0001")))
                .andExpect(content().string(containsString("確定済み")))
                .andExpect(content().string(containsString("予約版 1")))
                .andExpect(content()
                        .string(containsString("2026-10-08 09:30 Asia/Tokyo（UTC+09:00）（UTC 2026-10-08 00:30）")))
                .andExpect(content().string(containsString("RC-2026-0001 版 1")))
                .andExpect(content().string(containsString("一般貨物 パレット 10 個 1,200 kg")))
                // 欄の名前「追跡の開始」と重ならないよう「処理中」だけにする（Bolt 25 レビュー U-6。Bolt 25b）
                .andExpect(content().string(containsString(">処理中<")))
                .andExpect(content().string(not(containsString("追跡の開始を待っています"))))
                .andExpect(content().string(containsString("<a href=\"/staff/bookings\">予約一覧へ戻る</a>")))
                .andExpect(content().string(containsString("確定時刻")))
                .andExpect(content().string(not(containsString("commit 時刻"))))
                // 自動では変わらないことと、処理中のまま終わらないときの問い合わせ先を示す（Bolt 25 レビュー U-1・U-2）
                .andExpect(content().string(containsString("この画面は自動では変わりません。画面を更新するか、下のリンクから開き直して確かめてください。")))
                .andExpect(content().string(containsString("しばらくしても処理中のままなら、システム管理者にお問い合わせください。")))
                // 更新の操作をブラウザーの機能だけに頼らない（U-3）。案内を「追跡の開始」の欄に結び付ける（U-4）
                .andExpect(content()
                        .string(containsString(
                                "<a href=\"/staff/bookings/CTABCDEFGH2345\">CTABCDEFGH2345 の予約の詳細を開き直す</a>")))
                .andExpect(content().string(containsString("aria-describedby=\"tracking-start-hint\"")))
                .andExpect(content().string(not(containsString(">完了<"))))
                .andExpect(
                        content().string(not(containsString(booking.id().value().toString()))));
    }

    /** 予約サガが完了したら「追跡の開始: 完了」を示し、更新の案内は出さない（ADR-015、T-69。Bolt 25）。 */
    @Test
    void 予約の詳細に追跡の開始が完了したことを示し更新の案内は出さない() throws Exception {
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        BookingFixture.terms(),
                        BookingFixture.TRACKING_NUMBER,
                        BookingFixture.SALES,
                        new UtcInstant(Instant.parse("2026-10-08T00:30:00Z")))
                .booking();
        given(queryService.detail(BookingFixture.TRACKING_NUMBER))
                .willReturn(Optional.of(new BookingDetail(booking, BookingSagaStatus.COMPLETED)));

        mockMvc.perform(get("/staff/bookings/CTABCDEFGH2345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">完了<")))
                .andExpect(content().string(not(containsString("処理中"))))
                .andExpect(content().string(not(containsString("この画面は自動では変わりません"))))
                .andExpect(content().string(not(containsString("開き直す"))));
    }

    /** S-10 予約一覧の最小の表示（Bolt 25b）。確定時刻の新しい順で、追跡番号から S-24 を開ける。 */
    @Test
    void 予約一覧に追跡番号と見積りと確定時刻と追跡の開始を新しい順に示す() throws Exception {
        given(queryService.recent())
                .willReturn(new RecentBookings(
                        List.of(
                                recent(
                                        "CTABCDEFGH2345",
                                        "TR-2026-0002",
                                        1,
                                        "2026-10-08T00:30:00Z",
                                        BookingSagaStatus.COMPLETED),
                                recent(
                                        "CTBCDEFGHJ2345",
                                        "TR-2026-0001",
                                        2,
                                        "2026-10-07T00:30:00Z",
                                        BookingSagaStatus.IN_PROGRESS)),
                        false));

        String html = mockMvc.perform(get("/staff/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>予約一覧</h1>")))
                .andExpect(content().string(containsString("確定した予約（確定時刻の新しい順）")))
                .andExpect(content()
                        .string(containsString("<a href=\"/staff/bookings/CTABCDEFGH2345\">CTABCDEFGH2345</a>")))
                .andExpect(content().string(containsString("TR-2026-0002 見積 1")))
                .andExpect(content()
                        .string(containsString("2026-10-08 09:30 Asia/Tokyo（UTC+09:00）（UTC 2026-10-08 00:30）")))
                .andExpect(content().string(containsString(">完了<")))
                .andExpect(content().string(containsString(">処理中<")))
                .andExpect(content().string(not(containsString("新しい 50 件だけを示しています"))))
                .andExpect(content().string(not(containsString("確定した予約はまだありません"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.indexOf("CTABCDEFGH2345"))
                .isLessThan(html.indexOf("CTBCDEFGHJ2345"));
    }

    @Test
    void 予約がなければその旨を示す() throws Exception {
        given(queryService.recent()).willReturn(new RecentBookings(List.of(), false));

        mockMvc.perform(get("/staff/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("確定した予約はまだありません。")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void 上限を超えたら新しい50件だけを示していることを示す() throws Exception {
        given(queryService.recent())
                .willReturn(new RecentBookings(
                        List.of(recent(
                                "CTABCDEFGH2345",
                                "TR-2026-0001",
                                1,
                                "2026-10-08T00:30:00Z",
                                BookingSagaStatus.IN_PROGRESS)),
                        true));

        mockMvc.perform(get("/staff/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("新しい 50 件だけを示しています。")));
    }

    /** 追跡の開始の表示は UI 設計の共通部品「処理中表示」にそろえる。失敗は処理中のまま示す（Bolt 25b の確認ポイント 6。T-57）。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "IN_PROGRESS, 処理中",
        "COMPLETED, 完了",
        "FAILED, 処理中",
        "NEEDS_HUMAN, 有人確認要（担当者が確認します）"
    })
    void 予約一覧の追跡の開始は予約サガの状態ごとに処理中表示の言葉で示す(BookingSagaStatus status, String label) throws Exception {
        given(queryService.recent())
                .willReturn(new RecentBookings(
                        List.of(recent("CTABCDEFGH2345", "TR-2026-0001", 1, "2026-10-08T00:30:00Z", status)), false));

        mockMvc.perform(get("/staff/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">" + label + "<")));
    }

    private static RecentBookings.Row recent(
            String trackingNumber,
            String transportRequestNumber,
            int quotationNo,
            String committedAt,
            BookingSagaStatus status) {
        return new RecentBookings.Row(
                new TrackingNumber(trackingNumber),
                transportRequestNumber,
                quotationNo,
                new UtcInstant(Instant.parse(committedAt)),
                status);
    }

    @Test
    void ない追跡番号と追跡番号の形でない番号は404にする() throws Exception {
        given(queryService.detail(new TrackingNumber("CTZZZZZZZZZZZZ"))).willReturn(Optional.empty());

        mockMvc.perform(get("/staff/bookings/CTZZZZZZZZZZZZ")).andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/bookings/XX")).andExpect(status().isNotFound());
    }
}
