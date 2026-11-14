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

import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.ReopenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseLookup;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseDetail;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** C-15 問い合わせの詳細と再開の依頼（荷主担当者。US-23 AC2・AC3。Bolt 42-3）。 */
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = CustomerServiceCaseController.class)
class CustomerServiceCaseDetailControllerTest {

    private static final CaseNumber NUMBER = new CaseNumber("SC-2026-0057");
    private static final String DETAIL = "/customer/inquiries/" + NUMBER.value();
    private static final Instant OPENED = Instant.parse("2026-11-16T01:00:00Z");
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-0000042c0001"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ServiceCaseQueryService queryService;

    @MockitoBean
    ServiceCaseActionService actionService;

    @MockitoBean
    TrackingOutOfScopeResponder outOfScopeResponder;

    private static ServiceCase received() {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "貨物の所在", "CS")
                .withShipper(TestActors.SHIPPER_COMPANY);
        ServiceDeadlines deadlines = new ServiceDeadlines(
                OPENED, Instant.parse("2026-11-16T05:00:00Z"), Instant.parse("2026-11-17T05:00:00Z"));
        return ((ServiceCaseOpening.Opened) ServiceCase.open(draft, NUMBER, deadlines, new UtcInstant(OPENED)))
                .serviceCase();
    }

    private static ServiceCase answered() {
        return received()
                .receive(STAFF, new UtcInstant(Instant.parse("2026-11-16T02:00:00Z")))
                .updateProgress(
                        "港湾に再送を依頼",
                        new UtcInstant(Instant.parse("2026-11-16T09:00:00Z")),
                        STAFF,
                        new UtcInstant(Instant.parse("2026-11-16T02:20:00Z")))
                .recordAnswer("港湾から再送されました", STAFF, new UtcInstant(Instant.parse("2026-11-16T03:00:00Z")));
    }

    private void found(ServiceCase serviceCase) {
        given(queryService.customerDetail(any(), eq(NUMBER)))
                .willReturn(new CustomerServiceCaseLookup.Found(new ServiceCaseDetail(serviceCase, false)));
    }

    @Test
    void 行1_詳細は進捗と回答の履歴を荷主の日時表示で示し担当者の利用者IDを出さない() throws Exception {
        found(answered());

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>問い合わせ SC-2026-0057</h1>")))
                .andExpect(content().string(containsString("回答済み")))
                .andExpect(content().string(containsString("港湾に再送を依頼")))
                .andExpect(content().string(containsString("港湾から再送されました")))
                .andExpect(content().string(containsString("2026-11-16 12:00 Asia/Tokyo（UTC+09:00）")))
                .andExpect(content().string(not(containsString("利用者 ID"))));
    }

    @Test
    void 行2_回答済みなら未解決の回答の欄と再開の依頼のボタンを出す() throws Exception {
        found(answered());

        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("action=\"/customer/inquiries/SC-2026-0057/reopen\"")))
                .andExpect(content().string(containsString("name=\"customerResponse\"")))
                .andExpect(content().string(containsString("まだ解決していません")));
    }

    @Test
    void 行3_回答済みでなければ再開の依頼を出さない() throws Exception {
        found(received());

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/reopen\""))));
    }

    @Test
    void 行4_他社の問い合わせは試行を記録して404() throws Exception {
        given(queryService.customerDetail(any(), eq(NUMBER))).willReturn(new CustomerServiceCaseLookup.OutOfScope());
        given(outOfScopeResponder.recordServiceCaseAndNotFound(any(), eq("SC-2026-0057")))
                .willReturn(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());

        then(outOfScopeResponder).should().recordServiceCaseAndNotFound(any(), eq("SC-2026-0057"));
    }

    @Test
    void 行5_ない問い合わせと形式の誤りは記録せず404() throws Exception {
        given(queryService.customerDetail(any(), any())).willReturn(new CustomerServiceCaseLookup.NotFound());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());
        mockMvc.perform(get("/customer/inquiries/not-a-number")).andExpect(status().isNotFound());

        then(outOfScopeResponder).shouldHaveNoInteractions();
    }

    @Test
    void 行6_再開を依頼すると未解決の回答を付けて再開し詳細へ戻って結果を示す() throws Exception {
        found(answered());
        given(actionService.reopen(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.Done(NUMBER, ServiceCaseStatus.IN_PROGRESS));

        mockMvc.perform(post(DETAIL + "/reopen").param("customerResponse", "まだ解決していません"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "再開を依頼しました。担当者が対応します。"));

        then(actionService).should().reopen(any(), eq(new ReopenServiceCaseCommand(NUMBER, "まだ解決していません")));
    }

    @Test
    void 行7_他社の問い合わせの再開の依頼は試行を記録して404で再開しない() throws Exception {
        given(queryService.customerDetail(any(), eq(NUMBER))).willReturn(new CustomerServiceCaseLookup.OutOfScope());
        given(outOfScopeResponder.recordServiceCaseAndNotFound(any(), eq("SC-2026-0057")))
                .willReturn(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(post(DETAIL + "/reopen").param("customerResponse", "未解決"))
                .andExpect(status().isNotFound());

        then(outOfScopeResponder).should().recordServiceCaseAndNotFound(any(), eq("SC-2026-0057"));
        then(actionService).should(never()).reopen(any(), any());
    }

    @Test
    void 行8_状態が合わない再開の依頼は詳細へ戻って理由を示す() throws Exception {
        found(answered());
        given(actionService.reopen(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.InvalidState(ServiceCaseStatus.IN_PROGRESS));

        mockMvc.perform(post(DETAIL + "/reopen").param("customerResponse", "未解決"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attributeExists("problem"));
    }
}
