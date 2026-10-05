package com.example.cargotracker.quotation.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLineInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Violation;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = StaffQuotationController.class,
        properties = {
            "cargotracker.provisional-actor.shipper-company-id=00000000-0000-0000-0000-000000000001",
            "cargotracker.provisional-actor.user-id=00000000-0000-0000-0000-000000000101",
            "cargotracker.provisional-actor.staff-user-id=00000000-0000-0000-0000-000000000301",
            "cargotracker.provisional-consignees.companies[0].id=00000000-0000-0000-0000-000000000201",
            "cargotracker.provisional-consignees.companies[0].name=荷受人 A（仮）"
        })
class StaffQuotationControllerTest {

    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
    private static final TransportRequestId ID =
            new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ProvisionalActorProperties.class, ProvisionalConsigneeProperties.class})
    static class Properties {}

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QuotationCommandService commandService;

    @MockitoBean
    StaffQuotationQueryService queryService;

    @MockitoBean
    StaffTransportRequestQueryService transportRequestQueryService;

    private static UtcInstant tokyo(String dateTime) {
        return new UtcInstant(LocalDateTime.parse(dateTime).atZone(TOKYO).toInstant());
    }

    private void transportRequestExists() {
        TransportRequest request = TransportRequest.submit(
                ID,
                NUMBER,
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
        given(transportRequestQueryService.findByNumber(NUMBER)).willReturn(Optional.of(request));
    }

    private static Quotation pendingApproval() {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), ID, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), new UtcInstant(Instant.parse("2026-10-05T04:00:00Z")));
        return quotation;
    }

    @Test
    void 作成画面に10行の料金明細の欄と通貨と有効期限と経路方針の欄と対象の版を示す() throws Exception {
        transportRequestExists();

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001/quotations/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("対象: TR-2026-0001 版 1")))
                .andExpect(content().string(containsString("明細 10 の内容")))
                .andExpect(content().string(not(containsString("明細 11 の内容"))))
                .andExpect(content().string(containsString("有効期限（日本時間）")))
                .andExpect(content().string(containsString("概算の到着日時（日本時間）")));
    }

    @Test
    void 入力した明細と日本時間の日時を見積りの入力にして算出し算出した見積りへリダイレクトする() throws Exception {
        transportRequestExists();
        QuotationInput expected = new QuotationInput(
                List.of(new PricingLineInput("海上運賃", new BigDecimal("3200.00"), "年間契約")),
                Currency.USD,
                tokyo("2099-10-08T18:00"),
                List.of(new Location("SGSIN"), new Location("LKCMB")),
                tokyo("2099-10-10T09:00"),
                tokyo("2099-10-30T18:00"));
        given(commandService.calculate(new CalculateQuotationCommand(NUMBER, expected)))
                .willReturn(new CalculationOutcome.Calculated(NUMBER, 1));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations")
                        .param("lines[0].description", "海上運賃")
                        .param("lines[0].amount", "3200.00")
                        .param("lines[0].contractReference", "年間契約")
                        .param("lines[1].description", "")
                        .param("currency", "USD")
                        .param("expiresAt", "2099-10-08 18:00")
                        .param("via", " sgsin, LKCMB ")
                        .param("departureAt", "2099-10-10 09:00")
                        .param("arrivalAt", "2099-10-30 18:00"))
                .andExpect(redirectedUrl("/staff/transport-requests/TR-2026-0001/quotations/1"))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 を算出しました。内容を確かめて社内承認してください"));
    }

    @Test
    void 形式の誤りはまとめてエラー要約に示し算出しない() throws Exception {
        transportRequestExists();

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations")
                        .param("lines[2].description", "割増")
                        .param("lines[2].amount", "高い")
                        .param("currency", "GBP")
                        .param("expiresAt", "2099/10/08")
                        .param("via", "SG"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors(
                                "quotationForm", "lines[2].amount", "currency", "expiresAt", "via"))
                .andExpect(content().string(containsString(">明細 3 の金額: 数字で入力してください</a>")))
                .andExpect(content().string(containsString(">有効期限: 2026-11-02 09:00 の形（日本時間）で入力してください</a>")))
                .andExpect(content().string(containsString("value=\"2099/10/08\"")));

        then(commandService).should(never()).calculate(any());
    }

    @Test
    void 業務の規則の違反は欄の位置に戻してエラー要約と項目の下に示す() throws Exception {
        transportRequestExists();
        given(commandService.calculate(any()))
                .willReturn(new CalculationOutcome.Invalid(new QuotationViolations(List.of(
                        new Violation(Item.PRICING_LINES, Reason.AMOUNT_NOT_POSITIVE, 1),
                        new Violation(Item.EXPIRES_AT, Reason.NOT_AFTER_CALCULATION)))));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations")
                        .param("lines[4].description", "割増")
                        .param("lines[4].amount", "0")
                        .param("currency", "USD")
                        .param("expiresAt", "2026-10-01 09:00"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("quotationForm", "lines[4].amount", "expiresAt"))
                .andExpect(content().string(containsString(">明細 5 の金額: 0 より大きい値で入力してください</a>")))
                .andExpect(content().string(containsString(">有効期限: いまより後の日時を入力してください</a>")));
    }

    @Test
    void 見積りを作れないときは受付一覧に戻して理由を示す() throws Exception {
        transportRequestExists();
        given(commandService.calculate(any()))
                .willReturn(new CalculationOutcome.Rejected(QuotationRejection.ALREADY_QUOTED));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations")
                        .param("currency", "USD"))
                .andExpect(redirectedUrl("/staff/transport-requests"))
                .andExpect(flash().attribute("problem", "TR-2026-0001 にはすでに見積りがあります"));
    }

    @Test
    void 算出した見積りに料金明細と合計と有効期限と経路方針と提示のボタンを示す() throws Exception {
        transportRequestExists();
        given(queryService.find(NUMBER, 1)).willReturn(Optional.of(pendingApproval()));

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001/quotations/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見積り TR-2026-0001 見積 1")))
                .andExpect(content().string(containsString("承認待ち")))
                .andExpect(content().string(containsString("3,200.00 USD")))
                .andExpect(content().string(containsString("3,730.00 USD")))
                .andExpect(content()
                        .string(containsString("2099-10-08 18:00 Asia/Tokyo（UTC+09:00）（UTC 2099-10-08 09:00）")))
                .andExpect(content().string(containsString("SGSIN")))
                .andExpect(content().string(containsString("社内承認して提示する")));
    }

    @Test
    void ない見積りは見つからない() throws Exception {
        transportRequestExists();

        mockMvc.perform(get("/staff/transport-requests/TR-2026-0001/quotations/9"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/staff/transport-requests/TR-2026/quotations/new")).andExpect(status().isNotFound());
    }

    @Test
    void 社内承認して提示すると仮の営業担当者で提示し受付一覧に結果を示す() throws Exception {
        given(commandService.present(new PresentQuotationCommand(NUMBER, 1, STAFF)))
                .willReturn(new PresentationOutcome.Presented(NUMBER, 1));

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation"))
                .andExpect(redirectedUrl("/staff/transport-requests"))
                .andExpect(flash().attribute("result", "TR-2026-0001 見積 1 を提示しました"));
    }

    @Test
    void 提示できないときと競合したときは見積りに戻して理由を示す() throws Exception {
        given(commandService.present(any()))
                .willReturn(new PresentationOutcome.Rejected(QuotationRejection.NOT_PENDING_APPROVAL))
                .willReturn(new PresentationOutcome.Conflict());

        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation"))
                .andExpect(redirectedUrl("/staff/transport-requests/TR-2026-0001/quotations/1"))
                .andExpect(flash().attribute("problem", "TR-2026-0001 見積 1 は承認待ちでないため提示できません"));
        mockMvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation"))
                .andExpect(flash().attribute("problem", "他の利用者が先に更新しました。内容を確かめてください"));
    }
}
