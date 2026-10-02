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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
        controllers = TransportRequestController.class,
        properties = {
            "cargotracker.provisional-actor.shipper-company-id=00000000-0000-0000-0000-000000000001",
            "cargotracker.provisional-actor.user-id=00000000-0000-0000-0000-000000000101",
            "cargotracker.provisional-consignees.companies[0].id=00000000-0000-0000-0000-000000000201",
            "cargotracker.provisional-consignees.companies[0].name=荷受人 A（仮）",
            "cargotracker.provisional-consignees.companies[1].id=00000000-0000-0000-0000-000000000202",
            "cargotracker.provisional-consignees.companies[1].name=荷受人 B（仮）"
        })
class TransportRequestControllerTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final TransportRequestId ID =
            new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final SubmissionOutcome SUBMITTED = new SubmissionOutcome.Submitted(ID, NUMBER);

    /** 画面の必須条件をそろえた入力から作られる、輸送条件の入力。希望到着期限は日本時間の 09:00（UTC 00:00）。 */
    private static final ShipmentTermsInput COMPLETE_INPUT = new ShipmentTermsInput(
            ShipmentTermsFixture.CONSIGNEE,
            new Location("JPTYO"),
            new Location("NLRTM"),
            new UtcInstant(Instant.parse("2026-11-02T00:00:00Z")),
            CargoCategory.GENERAL,
            PackageType.PALLET,
            12,
            new BigDecimal("8400"),
            new BigDecimal("32.5"));

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ProvisionalActorProperties.class, ProvisionalConsigneeProperties.class})
    static class Properties {}

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransportRequestCommandService commandService;

    @MockitoBean
    TransportRequestQueryService queryService;

    /** 必須条件をそろえた画面の入力。{@code overrides} は「項目名, 値」の組で、その項目の値を置き換える。 */
    private static MockHttpServletRequestBuilder completeForm(String... overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("consignee", "00000000-0000-0000-0000-000000000201");
        values.put("origin", "JPTYO");
        values.put("destination", "NLRTM");
        values.put("arrivalDeadline", "2026-11-02 09:00");
        values.put("cargoCategory", "GENERAL");
        values.put("packageType", "PALLET");
        values.put("packageCount", "12");
        values.put("grossWeightKg", "8400");
        values.put("volumeM3", "32.5");
        for (int i = 0; i < overrides.length; i += 2) {
            values.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = post("/customer/transport-requests");
        values.forEach(request::param);
        return request;
    }

    @Test
    void 見積依頼の作成画面に必須条件の入力欄と仮の荷受人の一覧を表示する() throws Exception {
        mockMvc.perform(get("/customer/transport-requests/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(content().string(containsString("荷受人")))
                .andExpect(content().string(containsString("荷受人 A（仮）")))
                .andExpect(content().string(containsString("出発地（UN/LOCODE）")))
                .andExpect(content().string(containsString("目的地（UN/LOCODE）")))
                .andExpect(content().string(containsString("希望到着期限（日本時間）")))
                .andExpect(content().string(containsString("Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("貨物種別")))
                .andExpect(content().string(containsString("荷姿")))
                .andExpect(content().string(containsString("個数")))
                .andExpect(content().string(containsString("総重量（kg）")))
                .andExpect(content().string(containsString("容積（m3）")))
                .andExpect(content().string(not(containsString("error-summary"))));
    }

    @Test
    void 必須条件をそろえて提出すると日本時間の期限をUTCにして提出し業務番号の完了画面へリダイレクトする() throws Exception {
        SubmitTransportRequestCommand expected = new SubmitTransportRequestCommand(SHIPPER, USER, COMPLETE_INPUT);
        given(commandService.submit(expected)).willReturn(SUBMITTED);

        mockMvc.perform(completeForm()).andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001/submitted"));

        then(commandService).should().submit(expected);
    }

    @Test
    void 小文字と前後の空白はそろえて提出する() throws Exception {
        given(commandService.submit(any())).willReturn(SUBMITTED);

        mockMvc.perform(completeForm("origin", " jptyo ", "destination", "nlrtm"))
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001/submitted"));

        then(commandService).should().submit(new SubmitTransportRequestCommand(SHIPPER, USER, COMPLETE_INPUT));
    }

    @Test
    void 空欄の項目は入力なしとして提出し不足の判定はドメインに任せる() throws Exception {
        given(commandService.submit(any()))
                .willReturn(new SubmissionOutcome.Rejected(
                        new SubmissionViolations(List.of(new Violation(Item.ORIGIN, Reason.MISSING)))));

        mockMvc.perform(completeForm("origin", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("出発地を入力してください")));

        then(commandService)
                .should()
                .submit(new SubmitTransportRequestCommand(
                        SHIPPER,
                        USER,
                        new ShipmentTermsInput(
                                COMPLETE_INPUT.consigneeCompanyId(),
                                null,
                                COMPLETE_INPUT.destination(),
                                COMPLETE_INPUT.arrivalDeadline(),
                                COMPLETE_INPUT.cargoCategory(),
                                COMPLETE_INPUT.packageType(),
                                COMPLETE_INPUT.packageCount(),
                                COMPLETE_INPUT.grossWeightKg(),
                                COMPLETE_INPUT.volumeM3())));
    }

    @Test
    void 形式の誤りはまとめてエラー要約と項目の下に示し入力値を残して提出しない() throws Exception {
        mockMvc.perform(completeForm("origin", "TYO", "arrivalDeadline", "2026/11/02", "packageCount", "12個"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(model().attributeHasFieldErrors(
                                "transportRequestForm", "origin", "arrivalDeadline", "packageCount"))
                .andExpect(content().string(containsString("入力内容に 3 件の誤りがあります")))
                .andExpect(content().string(containsString("UN/LOCODE（国コード 2 文字 + 地点コード 3 文字、例: JPTYO）")))
                .andExpect(content().string(containsString("2026-11-02 09:00 の形")))
                .andExpect(content().string(containsString("個数は数字で入力してください")))
                .andExpect(content().string(containsString("value=\"TYO\"")))
                .andExpect(content().string(containsString("href=\"#origin\"")));

        then(commandService).should(never()).submit(any());
    }

    @Test
    void 業務の規則の違反は理由と直し方をエラー要約と項目の下に示し入力値を残す() throws Exception {
        given(commandService.submit(any()))
                .willReturn(new SubmissionOutcome.Rejected(new SubmissionViolations(List.of(
                        new Violation(Item.DESTINATION, Reason.SAME_AS_ORIGIN),
                        new Violation(Item.ARRIVAL_DEADLINE, Reason.NOT_AFTER_SUBMISSION),
                        new Violation(Item.GROSS_WEIGHT_KG, Reason.TOO_MANY_DECIMALS)))));

        mockMvc.perform(completeForm("destination", "JPTYO"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors(
                                "transportRequestForm", "destination", "arrivalDeadline", "grossWeightKg"))
                .andExpect(content().string(containsString("入力内容に 3 件の誤りがあります")))
                .andExpect(content().string(containsString("目的地が出発地と同じです。別の港を入力してください")))
                .andExpect(content().string(containsString("提出する時刻より後の日時を入力してください")))
                .andExpect(content().string(containsString("総重量（kg）は小数点以下 3 桁までで入力してください")))
                .andExpect(content().string(containsString("value=\"JPTYO\"")));
    }

    @Test
    void 特殊貨物の違反は対象外と手動窓口への相談を示す() throws Exception {
        given(commandService.submit(any()))
                .willReturn(new SubmissionOutcome.Rejected(
                        new SubmissionViolations(List.of(new Violation(Item.CARGO_CATEGORY, Reason.OUTSIDE_MVP)))));

        mockMvc.perform(completeForm("cargoCategory", "REEFER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("この画面では受け付けていません")))
                .andExpect(content().string(containsString("営業窓口")));
    }

    @Test
    void 仮の一覧にない荷受人は誤りにする() throws Exception {
        mockMvc.perform(completeForm("consignee", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("transportRequestForm", "consignee"))
                .andExpect(content().string(containsString("荷受人を一覧から選んでください")));

        then(commandService).should(never()).submit(any());
    }

    @Test
    void 提出の完了画面に業務番号と版と審査中と日本時間の提出時刻を表示し内部のIDを出さない() throws Exception {
        given(queryService.findByNumber(NUMBER))
                .willReturn(Optional.of(TransportRequest.submit(
                        ID,
                        NUMBER,
                        SHIPPER,
                        ShipmentTermsFixture.generalCargo(),
                        USER,
                        new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")))));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/submitted"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TR-2026-0001 版 1")))
                .andExpect(content().string(containsString("審査中")))
                .andExpect(content().string(containsString("2026-10-05 10:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(containsString("営業担当者が内容を審査し")))
                .andExpect(content().string(not(containsString(ID.value().toString()))));
    }

    @Test
    void 存在しない業務番号の完了画面は見つからない() throws Exception {
        given(queryService.findByNumber(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0099/submitted"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 業務番号の形式でない完了画面は見つからない() throws Exception {
        mockMvc.perform(get("/customer/transport-requests/{id}/submitted", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        then(queryService).should(never()).findByNumber(any());
    }
}
