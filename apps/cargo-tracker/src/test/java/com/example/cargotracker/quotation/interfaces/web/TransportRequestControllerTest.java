package com.example.cargotracker.quotation.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ResubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.DocumentFile;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Violation;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@WebMvcTest(
        controllers = TransportRequestController.class,
        properties = {
            "cargotracker.provisional-actor.shipper-company-id=00000000-0000-0000-0000-000000000001",
            "cargotracker.provisional-actor.user-id=00000000-0000-0000-0000-000000000101",
            "cargotracker.provisional-actor.staff-user-id=00000000-0000-0000-0000-000000000301",
            "cargotracker.provisional-consignees.companies[0].id=00000000-0000-0000-0000-000000000201",
            "cargotracker.provisional-consignees.companies[0].name=荷受人 A（仮）",
            "cargotracker.provisional-consignees.companies[1].id=00000000-0000-0000-0000-000000000202",
            "cargotracker.provisional-consignees.companies[1].name=荷受人 B（仮）"
        })
class TransportRequestControllerTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
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
        return completeForm(post("/customer/transport-requests"), overrides);
    }

    /** 必須条件をそろえた画面の入力を、指定の送り先に送る。 */
    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B completeForm(B request, String... overrides) {
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
    void 必須条件をそろえて提出すると日本時間の期限をUTCにして提出し見積依頼の詳細へリダイレクトして結果を示す() throws Exception {
        SubmitTransportRequestCommand expected = new SubmitTransportRequestCommand(SHIPPER, USER, COMPLETE_INPUT);
        given(commandService.submit(expected)).willReturn(SUBMITTED);

        mockMvc.perform(completeForm())
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"))
                .andExpect(flash().attribute("result", "TR-2026-0001 版 1 を提出しました"));

        then(commandService).should().submit(expected);
    }

    @Test
    void 小文字と前後の空白はそろえて提出する() throws Exception {
        given(commandService.submit(any())).willReturn(SUBMITTED);

        mockMvc.perform(completeForm("origin", " jptyo ", "destination", "nlrtm"))
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"));

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
                .andExpect(content().string(containsString("個数は 1 以上の整数で入力してください")))
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
                .andExpect(content().string(containsString("荷受人を選択肢から選んでください")));

        then(commandService).should(never()).submit(any());
    }

    private static TransportRequest underReview() {
        return TransportRequest.submit(
                ID,
                NUMBER,
                SHIPPER,
                ShipmentTermsFixture.generalCargo(),
                USER,
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
    }

    private static TransportRequest sentBack() {
        TransportRequest request = underReview();
        request.sendBack(1, STAFF, "目的地の確認が必要", "目的地の港", new UtcInstant(Instant.parse("2026-10-05T03:00:00Z")));
        return request;
    }

    private static TransportRequestSummary summary(TransportRequestStatus status) {
        return new TransportRequestSummary(
                NUMBER,
                1,
                status,
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                new Location("JPTYO"),
                new Location("NLRTM"),
                ShipmentTermsFixture.ARRIVAL_DEADLINE,
                CargoCategory.GENERAL);
    }

    /** 出し直しの画面の入力。{@code overrides} は「項目名, 値」の組。 */
    private static MockHttpServletRequestBuilder resubmitForm(String... overrides) {
        return completeForm(post("/customer/transport-requests/TR-2026-0001/versions"), overrides);
    }

    @Test
    void 見積依頼の一覧は仮の主体の荷主企業の輸送要求を状態といま誰の対応待ちかとともに示す() throws Exception {
        given(queryService.findSummaries(SHIPPER)).willReturn(List.of(summary(TransportRequestStatus.DRAFT)));

        mockMvc.perform(get("/customer/transport-requests"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/list"))
                .andExpect(content().string(containsString("TR-2026-0001")))
                .andExpect(content().string(containsString("差戻し（お客様の対応待ち）")))
                .andExpect(content().string(containsString("JPTYO → NLRTM")))
                .andExpect(content().string(containsString("2026-10-05 10:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(not(containsString(ID.value().toString()))));

        then(queryService).should().findSummaries(SHIPPER);
    }

    @Test
    void 見積依頼の詳細に業務番号と版と状態と輸送条件と日本時間の提出時刻を示し内部のIDを出さない() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/detail"))
                .andExpect(content().string(containsString("TR-2026-0001 版 1")))
                .andExpect(content().string(containsString("審査中（A 社の対応待ち）")))
                .andExpect(content().string(containsString("営業担当者が内容を審査しています")))
                .andExpect(content().string(containsString("NLRTM")))
                .andExpect(content().string(containsString("2026-10-05 10:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(not(containsString("編集して出し直す"))))
                .andExpect(content().string(not(containsString("<dt>差戻しの理由</dt>"))))
                .andExpect(content().string(not(containsString(ID.value().toString()))));
    }

    @Test
    void 差し戻された見積依頼の詳細は差戻しの理由と不足事項と編集への入口を示し判断者は出さない() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(sentBack()));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("差戻し（お客様の対応待ち）")))
                .andExpect(content().string(containsString("目的地の確認が必要")))
                .andExpect(content().string(containsString("目的地の港")))
                .andExpect(content().string(containsString("編集して出し直す")))
                .andExpect(content().string(not(containsString(STAFF.value().toString()))));
    }

    @Test
    void 詳細は仮の主体の荷主企業で絞って照会し他社や存在しない番号は見つからない() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.empty());

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001")).andExpect(status().isNotFound());
        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/edit")).andExpect(status().isNotFound());

        then(queryService).should(org.mockito.Mockito.times(2)).findByNumber(NUMBER, SHIPPER);
    }

    @Test
    void 業務番号の形式でない詳細は見つからない() throws Exception {
        mockMvc.perform(get("/customer/transport-requests/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        then(queryService).should(never()).findByNumber(any(), any());
    }

    @Test
    void 完了画面は詳細に統合してなくした() throws Exception {
        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/submitted"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 下書きの編集画面は現在の版の輸送条件を日本時間の期限で初期値にし新しい版になる旨を示す() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(sentBack()));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(model().attribute(
                                "transportRequestForm",
                                org.hamcrest.Matchers.allOf(
                                        org.hamcrest.Matchers.hasProperty(
                                                "destination", org.hamcrest.Matchers.is("NLRTM")),
                                        org.hamcrest.Matchers.hasProperty(
                                                "arrivalDeadline", org.hamcrest.Matchers.is("2099-11-02 09:00")),
                                        org.hamcrest.Matchers.hasProperty(
                                                "packageType", org.hamcrest.Matchers.is("PALLET")),
                                        org.hamcrest.Matchers.hasProperty(
                                                "grossWeightKg", org.hamcrest.Matchers.is("8400")))))
                .andExpect(content().string(containsString("見積依頼の編集")))
                .andExpect(content().string(containsString("出し直すと版 2 になります。業務番号は変わりません")))
                .andExpect(content()
                        .string(containsString("action=\"/customer/transport-requests/TR-2026-0001/versions\"")))
                .andExpect(content().string(containsString("出し直す")));
    }

    @Test
    void 下書きでない見積依頼の編集画面は詳細に戻して理由を示す() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(underReview()));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/edit"))
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"))
                .andExpect(flash().attribute("result", "TR-2026-0001 版 1 は審査中のため出し直せません"));
    }

    @Test
    void 出し直すと仮の主体の荷主企業で再提出し詳細へリダイレクトして新しい版を示す() throws Exception {
        ResubmitTransportRequestCommand expected =
                new ResubmitTransportRequestCommand(NUMBER, SHIPPER, USER, COMPLETE_INPUT);
        given(commandService.resubmit(expected)).willReturn(new ResubmissionOutcome.Resubmitted(NUMBER, 2));

        mockMvc.perform(resubmitForm())
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"))
                .andExpect(flash().attribute("result", "TR-2026-0001 版 2 を出し直しました"));

        then(commandService).should().resubmit(expected);
    }

    @Test
    void 出し直しの不足はエラー要約で示し編集画面に入力値を残す() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(sentBack()));
        given(commandService.resubmit(any()))
                .willReturn(new ResubmissionOutcome.Invalid(
                        new SubmissionViolations(List.of(new Violation(Item.DESTINATION, Reason.MISSING)))));

        mockMvc.perform(resubmitForm("destination", "", "origin", "KRPUS"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(content().string(containsString("error-summary")))
                .andExpect(content().string(containsString("value=\"KRPUS\"")))
                .andExpect(content().string(containsString("出し直すと版 2 になります")));
    }

    @Test
    void 業務の規則の違反は編集画面にエラー要約で示す() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(sentBack()));
        given(commandService.resubmit(any()))
                .willReturn(new ResubmissionOutcome.Invalid(
                        new SubmissionViolations(List.of(new Violation(Item.DESTINATION, Reason.SAME_AS_ORIGIN)))));

        mockMvc.perform(resubmitForm("destination", "JPTYO"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(content().string(containsString("error-summary")));
    }

    @Test
    void 下書きでなくなった見積依頼の出し直しは詳細に戻して理由を示す() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(underReview()));
        given(commandService.resubmit(any()))
                .willReturn(new ResubmissionOutcome.Rejected(ResubmissionRejection.NOT_DRAFT));

        mockMvc.perform(resubmitForm())
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"))
                .andExpect(flash().attribute("result", "TR-2026-0001 版 1 は審査中のため出し直せません"));
    }

    @Test
    void 同時の更新で出し直せなかったら詳細に戻して先に更新されたことを示す() throws Exception {
        given(commandService.resubmit(any())).willReturn(new ResubmissionOutcome.Conflict());

        mockMvc.perform(resubmitForm())
                .andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"))
                .andExpect(flash().attribute("result", "他の利用者が先に更新しました。内容を確かめてから出し直してください"));
    }

    @Test
    void 他社や存在しない番号の出し直しは見つからない() throws Exception {
        given(commandService.resubmit(any())).willReturn(new ResubmissionOutcome.NotFound());

        mockMvc.perform(resubmitForm()).andExpect(status().isNotFound());
    }

    private static RequiredDocument invoiceDocument() {
        return new RequiredDocument(
                1,
                DocumentType.COMMERCIAL_INVOICE,
                "送り状 invoice.pdf",
                DocumentMediaType.PDF,
                14,
                "a".repeat(64),
                "quotation/" + ID.value() + "/" + UUID.randomUUID());
    }

    private static TransportRequest underReviewWithInvoice() {
        return TransportRequest.submit(
                ID,
                NUMBER,
                SHIPPER,
                ShipmentTermsFixture.generalCargo().withDocuments(List.of(invoiceDocument())),
                USER,
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
    }

    @Test
    void 選んだファイルを種類ごとの添付にして提出し選んでいない欄は無視する() throws Exception {
        byte[] pdf = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        SubmitTransportRequestCommand expected = new SubmitTransportRequestCommand(
                SHIPPER,
                USER,
                COMPLETE_INPUT,
                List.of(
                        new RequiredDocumentAttachment(DocumentType.COMMERCIAL_INVOICE, "invoice.pdf", pdf),
                        new RequiredDocumentAttachment(DocumentType.OTHER, "memo.pdf", pdf)));
        given(commandService.submit(expected)).willReturn(SUBMITTED);
        MockMultipartHttpServletRequestBuilder request = multipart("/customer/transport-requests");
        request.file(new MockMultipartFile("commercialInvoice", "invoice.pdf", "application/pdf", pdf));
        request.file(new MockMultipartFile("packingList", "", "application/octet-stream", new byte[0]));
        request.file(new MockMultipartFile("otherDocuments", "memo.pdf", "application/pdf", pdf));

        mockMvc.perform(completeForm(request)).andExpect(redirectedUrl("/customer/transport-requests/TR-2026-0001"));

        then(commandService).should().submit(expected);
    }

    @Test
    void 詳細に現在の版の書類の一覧と取得のリンクを示す() throws Exception {
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(underReviewWithInvoice()));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("商業送り状")))
                .andExpect(content().string(containsString("送り状 invoice.pdf")))
                .andExpect(content()
                        .string(containsString(
                                "href=\"/customer/transport-requests/TR-2026-0001/versions/1/documents/1\"")));
    }

    @Test
    void 書類はattachmentとnosniffと形式でダウンロードさせる() throws Exception {
        byte[] content = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        given(queryService.findDocument(NUMBER, SHIPPER, 1, 1))
                .willReturn(Optional.of(new DocumentFile(invoiceDocument(), content)));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/versions/1/documents/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(content));
    }

    @Test
    void 他社やない書類の取得は見つからない() throws Exception {
        given(queryService.findDocument(any(), any(), anyInt(), anyInt())).willReturn(Optional.empty());

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/versions/1/documents/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/customer/transport-requests/{id}/versions/1/documents/1", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        then(queryService).should().findDocument(NUMBER, SHIPPER, 1, 1);
    }

    @Test
    void 編集画面に前の版の書類を引き継ぐことを示す() throws Exception {
        TransportRequest request = underReviewWithInvoice();
        request.sendBack(1, STAFF, "梱包明細が必要", "梱包明細", new UtcInstant(Instant.parse("2026-10-05T03:00:00Z")));
        given(queryService.findByNumber(NUMBER, SHIPPER)).willReturn(Optional.of(request));

        mockMvc.perform(get("/customer/transport-requests/TR-2026-0001/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("前の版の書類（出し直しても引き継ぎます）")))
                .andExpect(content().string(containsString("商業送り状: 送り状 invoice.pdf（1 KB）")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")));
    }
}
