package com.example.cargotracker.tracking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.ReceiveServiceCaseOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseDetail;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseList;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseListFilter;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseListItem;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-14 有人案件の一覧と S-15 有人案件の詳細・受領（社内の運用役割。Bolt 40-11）。認可はセキュリティの統合テストで確かめる。 */
@WithAuthenticatedActor(Role.CUSTOMER_SUPPORT)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = ServiceCaseController.class)
class ServiceCaseControllerTest {

    private static final String LIST = "/staff/service-cases";
    private static final CaseNumber NUMBER = new CaseNumber("SC-2026-0057");
    private static final String DETAIL = LIST + "/" + NUMBER.value();
    private static final Instant OPENED = Instant.parse("2026-11-16T01:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ServiceCaseQueryService queryService;

    @MockitoBean
    ServiceCaseCommandService commandService;

    @MockitoBean
    com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService actionService;

    private static ServiceCase received() {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "貨物の所在", "CS")
                .withShipper(new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040b1101")));
        ServiceDeadlines deadlines = new ServiceDeadlines(
                OPENED, Instant.parse("2026-11-16T05:00:00Z"), Instant.parse("2026-11-17T05:00:00Z"));
        return ((ServiceCaseOpening.Opened) ServiceCase.open(draft, NUMBER, deadlines, new UtcInstant(OPENED)))
                .serviceCase();
    }

    private static ServiceCase inProgress(UserId assignee) {
        ServiceCase base = received().receive(assignee, new UtcInstant(Instant.parse("2026-11-16T02:00:00Z")));
        return base.updateProgress(
                "港湾に再送を依頼",
                new UtcInstant(Instant.parse("2026-11-16T09:00:00Z")),
                assignee,
                new UtcInstant(Instant.parse("2026-11-16T02:20:00Z")));
    }

    private static ServiceCaseListItem item(CaseNumber number, ServiceCaseStatus status, boolean overdue) {
        return new ServiceCaseListItem(
                number,
                ServiceCaseKind.CUSTOMER_INQUIRY,
                ServicePriority.NORMAL,
                "CS",
                status,
                new UtcInstant(Instant.parse("2026-11-16T05:00:00Z")),
                overdue);
    }

    // S-14 一覧

    @Test
    void 行1_一覧に受付番号と種類と状態と受領期限を示し期限超過には印を付ける() throws Exception {
        given(queryService.list(any(), eq(ServiceCaseListFilter.OPEN)))
                .willReturn(Optional.of(new ServiceCaseList(
                        List.of(
                                item(NUMBER, ServiceCaseStatus.RECEIVED, true),
                                item(new CaseNumber("SC-2026-0058"), ServiceCaseStatus.IN_PROGRESS, false)),
                        false,
                        200)));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SC-2026-0057")))
                .andExpect(content().string(containsString("顧客照会")))
                .andExpect(content().string(containsString("受付済み")))
                .andExpect(content().string(containsString("対応中")))
                .andExpect(content().string(containsString("期限超過")))
                .andExpect(content().string(containsString("href=\"/staff/service-cases/SC-2026-0057\"")));
    }

    @Test
    void 行2_絞り込みの値を照会に渡し選んでいる絞り込みを示す() throws Exception {
        given(queryService.list(any(), eq(ServiceCaseListFilter.OVERDUE)))
                .willReturn(Optional.of(new ServiceCaseList(List.of(), false, 200)));

        mockMvc.perform(get(LIST).param("filter", "overdue"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("期限超過の案件はありません。")));
    }

    @Test
    void 行3_知らない絞り込みの値は404() throws Exception {
        mockMvc.perform(get(LIST).param("filter", "unknown")).andExpect(status().isNotFound());
    }

    @Test
    void 行4_件のないときは理由を示す() throws Exception {
        given(queryService.list(any(), eq(ServiceCaseListFilter.OPEN)))
                .willReturn(Optional.of(new ServiceCaseList(List.of(), false, 200)));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未終結の有人案件はありません。")));
    }

    @Test
    void 行5_打ち切ったときは件数の上限を示す() throws Exception {
        given(queryService.list(any(), eq(ServiceCaseListFilter.OPEN)))
                .willReturn(Optional.of(
                        new ServiceCaseList(List.of(item(NUMBER, ServiceCaseStatus.RECEIVED, false)), true, 200)));

        mockMvc.perform(get(LIST)).andExpect(content().string(containsString("受領期限の古い 200 件だけを示しています。")));
    }

    @Test
    void 行6_権限のない役割の一覧は403() throws Exception {
        given(queryService.list(any(), any())).willReturn(Optional.empty());

        mockMvc.perform(get(LIST)).andExpect(status().isForbidden());
    }

    // S-15 詳細

    @Test
    void 行7_受付済みの詳細に受領のボタンと期限と担当未確定を示す() throws Exception {
        given(queryService.detail(any(), eq(NUMBER))).willReturn(Optional.of(new ServiceCaseDetail(received(), false)));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SC-2026-0057")))
                .andExpect(content().string(containsString("顧客照会")))
                .andExpect(content().string(containsString("受付済み")))
                .andExpect(content().string(containsString("まだ受領されていません")))
                .andExpect(content().string(containsString("action=\"/staff/service-cases/SC-2026-0057/receive\"")))
                .andExpect(content().string(containsString("受領する")));
    }

    @Test
    void 行8_対応中の詳細に担当と受領時刻と進捗を示し受領のボタンを出さない() throws Exception {
        UserId assignee = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040b1111"));
        given(queryService.detail(any(), eq(NUMBER)))
                .willReturn(Optional.of(new ServiceCaseDetail(inProgress(assignee), false)));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("対応中")))
                .andExpect(content().string(containsString("港湾に再送を依頼")))
                .andExpect(content().string(containsString("2026-11-16 11:00 Asia/Tokyo")))
                .andExpect(content().string(not(containsString("受領する"))));
    }

    @Test
    void 行9_期限を過ぎた受付済みの詳細に期限超過を示す() throws Exception {
        given(queryService.detail(any(), eq(NUMBER))).willReturn(Optional.of(new ServiceCaseDetail(received(), true)));

        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("期限超過")))
                .andExpect(content().string(containsString("受領する")));
    }

    @Test
    void 行10_ない案件と誤った受付番号と権限のない役割の詳細は404() throws Exception {
        given(queryService.detail(any(), eq(NUMBER))).willReturn(Optional.empty());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());
        mockMvc.perform(get(LIST + "/" + "X".repeat(21))).andExpect(status().isNotFound());
    }

    // 受領（PRG）

    @Test
    void 行11_受領したら詳細へ戻り結果を示す() throws Exception {
        given(commandService.receive(any(), eq(new ReceiveServiceCaseCommand(NUMBER))))
                .willReturn(new ReceiveServiceCaseOutcome.Received(NUMBER));

        mockMvc.perform(post(DETAIL + "/receive"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "SC-2026-0057 を受領しました。対応中にしました。"));
    }

    @Test
    void 行12_受領できない状態と競合は警告として示す() throws Exception {
        given(commandService.receive(any(), any()))
                .willReturn(new ReceiveServiceCaseOutcome.NotReceivable(ServiceCaseStatus.IN_PROGRESS))
                .willReturn(new ReceiveServiceCaseOutcome.Conflict());

        mockMvc.perform(post(DETAIL + "/receive"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "この案件は受領できる状態ではありません（今の状態: 対応中）。最新の状態を確かめてください。"));
        mockMvc.perform(post(DETAIL + "/receive"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "ほかの更新が先にこの案件を変更しました。最新の状態を確かめ、必要なら、もう一度受領してください。"));
    }

    @Test
    void 行13_ない案件は404_権限なしは403_誤った受付番号はコマンドを呼ばない() throws Exception {
        given(commandService.receive(any(), any()))
                .willReturn(new ReceiveServiceCaseOutcome.NotFound())
                .willReturn(new ReceiveServiceCaseOutcome.NotPermitted());

        mockMvc.perform(post(DETAIL + "/receive")).andExpect(status().isNotFound());
        mockMvc.perform(post(DETAIL + "/receive")).andExpect(status().isForbidden());
        mockMvc.perform(post(LIST + "/" + "X".repeat(21) + "/receive")).andExpect(status().isNotFound());
        then(commandService).should(org.mockito.Mockito.times(2)).receive(any(), any());
        then(commandService)
                .should(never())
                .receive(any(), eq(new ReceiveServiceCaseCommand(new CaseNumber("SC-2026-0001"))));
    }
}
