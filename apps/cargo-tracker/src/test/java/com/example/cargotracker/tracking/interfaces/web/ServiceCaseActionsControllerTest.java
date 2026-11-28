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
import com.example.cargotracker.tracking.application.internal.commands.CloseServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.RecordServiceCaseAnswerCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReopenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.UpdateServiceCaseProgressCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseDetail;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseClosing;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-15 の進捗の更新・回答の記録・終結・再開の操作と、上位対応中の表示（Bolt 40-12）。業務の判定は操作のサービスのテストで確かめる。 */
@WithAuthenticatedActor(Role.CUSTOMER_SUPPORT)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = ServiceCaseController.class)
class ServiceCaseActionsControllerTest {

    private static final CaseNumber NUMBER = new CaseNumber("SC-2026-0057");
    private static final String DETAIL = "/staff/service-cases/" + NUMBER.value();
    private static final UserId STAFF = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040c1201"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ServiceCaseQueryService queryService;

    @MockitoBean
    ServiceCaseCommandService commandService;

    @MockitoBean
    ServiceCaseActionService actionService;

    private static ServiceCase received() {
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "貨物の所在", "CS")
                .withShipper(new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040b1101")));
        Instant opened = Instant.parse("2026-11-16T01:00:00Z");
        ServiceDeadlines deadlines = new ServiceDeadlines(
                opened, Instant.parse("2026-11-16T05:00:00Z"), Instant.parse("2026-11-17T05:00:00Z"));
        return ((ServiceCaseOpening.Opened) ServiceCase.open(draft, NUMBER, deadlines, new UtcInstant(opened)))
                .serviceCase();
    }

    private static ServiceCase inProgress() {
        return received().receive(STAFF, new UtcInstant(Instant.parse("2026-11-16T02:00:00Z")));
    }

    private static ServiceCase answered() {
        return inProgress().recordAnswer("再送を確認しました", STAFF, new UtcInstant(Instant.parse("2026-11-16T03:00:00Z")));
    }

    private static ServiceCase closed() {
        return ((ServiceCaseClosing.Closed) answered().close()).serviceCase();
    }

    private void show(ServiceCase serviceCase) {
        given(queryService.detail(any(), eq(NUMBER)))
                .willReturn(Optional.of(new ServiceCaseDetail(serviceCase, false)));
    }

    // 出し分け

    @Test
    void 行1_対応中は進捗と回答の操作だけを出す() throws Exception {
        show(inProgress());

        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("action=\"/staff/service-cases/SC-2026-0057/progress\"")))
                .andExpect(content().string(containsString("action=\"/staff/service-cases/SC-2026-0057/answer\"")))
                .andExpect(content().string(not(containsString("/close\""))))
                .andExpect(content().string(not(containsString("/reopen\""))));
    }

    @Test
    void 行2_回答済みは終結の操作だけを出す() throws Exception {
        show(answered());

        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("action=\"/staff/service-cases/SC-2026-0057/close\"")))
                .andExpect(content().string(not(containsString("/progress\""))))
                .andExpect(content().string(not(containsString("/answer\""))));
    }

    @Test
    void 行3_終結は再開の操作だけを出す() throws Exception {
        show(closed());

        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("action=\"/staff/service-cases/SC-2026-0057/reopen\"")))
                .andExpect(content().string(not(containsString("/close\""))));
    }

    @Test
    void 行4_受付済みは操作を出さず受領だけ_上位対応中の案件には回している旨を示す() throws Exception {
        show(received());
        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("/receive\"")))
                .andExpect(content().string(not(containsString("/progress\""))))
                .andExpect(content().string(not(containsString("上位の運用責任者へ回しています。"))));

        ServiceCase escalated = received()
                .escalate(new UtcInstant(Instant.parse("2026-11-16T06:00:00Z")))
                .orElseThrow();
        show(escalated);
        mockMvc.perform(get(DETAIL))
                .andExpect(content().string(containsString("上位対応中")))
                .andExpect(content().string(containsString("受領期限を過ぎたため、上位の運用責任者へ回しています。")))
                .andExpect(content().string(containsString("/receive\"")));
    }

    // 操作（PRG）

    @Test
    void 行5_進捗を更新したら詳細へ戻り結果を示す_次回更新日時は日本時間で渡す() throws Exception {
        given(actionService.updateProgress(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.Done(NUMBER, ServiceCaseStatus.IN_PROGRESS));

        mockMvc.perform(post(DETAIL + "/progress")
                        .param("content", "港湾に再送を依頼")
                        .param("nextUpdateAt", "2026-11-16 18:00"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("result", "進捗を更新しました。"));
        then(actionService)
                .should()
                .updateProgress(
                        any(),
                        eq(new UpdateServiceCaseProgressCommand(
                                NUMBER, "港湾に再送を依頼", new UtcInstant(Instant.parse("2026-11-16T09:00:00Z")))));
    }

    @Test
    void 行6_次回更新日時が空なら変えない_形式が誤っていれば操作を呼ばない() throws Exception {
        given(actionService.updateProgress(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.Done(NUMBER, ServiceCaseStatus.IN_PROGRESS));

        mockMvc.perform(post(DETAIL + "/progress").param("content", "確認中").param("nextUpdateAt", ""))
                .andExpect(redirectedUrl(DETAIL));
        then(actionService)
                .should()
                .updateProgress(any(), eq(new UpdateServiceCaseProgressCommand(NUMBER, "確認中", null)));

        mockMvc.perform(post(DETAIL + "/progress").param("content", "確認中").param("nextUpdateAt", "明日"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("problem", "次回更新日時は 2026-11-16 18:00 の形式で入れてください。"));
        then(actionService).should(org.mockito.Mockito.times(1)).updateProgress(any(), any());
    }

    @Test
    void 行7_回答_終結_再開の結果の文() throws Exception {
        given(actionService.recordAnswer(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.Done(NUMBER, ServiceCaseStatus.ANSWERED));
        given(actionService.close(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.Done(NUMBER, ServiceCaseStatus.CLOSED));
        given(actionService.reopen(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.Done(NUMBER, ServiceCaseStatus.IN_PROGRESS));

        mockMvc.perform(post(DETAIL + "/answer").param("content", "再送を確認しました"))
                .andExpect(flash().attribute("result", "回答を記録しました。回答済みにしました。"));
        mockMvc.perform(post(DETAIL + "/close")).andExpect(flash().attribute("result", "終結しました。"));
        mockMvc.perform(post(DETAIL + "/reopen").param("customerResponse", "まだ解決していません"))
                .andExpect(flash().attribute("result", "再開しました。対応中にしました。"));
        then(actionService).should().recordAnswer(any(), eq(new RecordServiceCaseAnswerCommand(NUMBER, "再送を確認しました")));
        then(actionService).should().close(any(), eq(new CloseServiceCaseCommand(NUMBER)));
        then(actionService).should().reopen(any(), eq(new ReopenServiceCaseCommand(NUMBER, "まだ解決していません")));
    }

    @Test
    void 行8_できない状態と入力の誤りと復旧照合と競合は警告として示す() throws Exception {
        given(actionService.close(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.InvalidState(ServiceCaseStatus.IN_PROGRESS))
                .willReturn(new ServiceCaseActionOutcome.ReconciliationIncomplete(
                        Set.of(MissingItem.RECONCILIATION_RESULT)))
                .willReturn(new ServiceCaseActionOutcome.Conflict());
        given(actionService.recordAnswer(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.InvalidInput("回答の内容は空でなく 4000 文字まで"));

        mockMvc.perform(post(DETAIL + "/close"))
                .andExpect(flash().attribute("problem", "この操作はできる状態ではありません（今の状態: 対応中）。最新の状態を確かめてください。"));
        mockMvc.perform(post(DETAIL + "/close")).andExpect(flash().attribute("problem", "復旧照合が完了していないため、終結できません。"));
        mockMvc.perform(post(DETAIL + "/close"))
                .andExpect(flash().attribute("problem", "ほかの更新が先にこの案件を変更しました。最新の状態を確かめ、必要なら、もう一度操作してください。"));
        mockMvc.perform(post(DETAIL + "/answer").param("content", ""))
                .andExpect(flash().attribute("problem", "回答の内容は空でなく 4000 文字まで"));
    }

    @Test
    void 行9_ない案件は404_権限なしは403_誤った受付番号は呼ばない() throws Exception {
        given(actionService.close(any(), any()))
                .willReturn(new ServiceCaseActionOutcome.NotFound())
                .willReturn(new ServiceCaseActionOutcome.NotPermitted());

        mockMvc.perform(post(DETAIL + "/close")).andExpect(status().isNotFound());
        mockMvc.perform(post(DETAIL + "/close")).andExpect(status().isForbidden());
        mockMvc.perform(post("/staff/service-cases/" + "X".repeat(21) + "/close"))
                .andExpect(status().isNotFound());
        then(actionService).should(org.mockito.Mockito.times(2)).close(any(), any());
        then(actionService).should(never()).reopen(any(), any());
    }
}
