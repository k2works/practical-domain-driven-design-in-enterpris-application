package com.example.cargotracker.tracking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseItem;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerServiceCaseList;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseQueryService;
import com.example.cargotracker.tracking.domain.model.entities.CaseAnswer;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
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

/** C-14 問い合わせ一覧（荷主担当者。US-23 AC1・AC4。Bolt 42-2）。認可は URL の規則（`/customer/**`）が担い、統合テストで確かめる。 */
@WithAuthenticatedActor(Role.SHIPPER)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = CustomerServiceCaseController.class)
class CustomerServiceCaseControllerTest {

    private static final String LIST = "/customer/inquiries";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ServiceCaseQueryService queryService;

    private static CustomerServiceCaseItem item(String number, ServiceCaseStatus status, Optional<CaseAnswer> answer) {
        return new CustomerServiceCaseItem(
                new CaseNumber(number), status, new UtcInstant(Instant.parse("2026-11-17T05:00:00Z")), answer);
    }

    private static CaseAnswer answer(String text) {
        return new CaseAnswer(
                1,
                text,
                new UserId(UUID.fromString("00000000-0000-0000-0000-0000042b0001")),
                new UtcInstant(Instant.parse("2026-11-16T03:00:00Z")),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void 行1_自社の問い合わせの受付番号と状態と次回更新と最新の回答を示す() throws Exception {
        given(queryService.customerList(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CustomerServiceCaseList(
                        List.of(
                                item("SC-2026-0002", ServiceCaseStatus.RECEIVED, Optional.empty()),
                                item("SC-2026-0001", ServiceCaseStatus.ANSWERED, Optional.of(answer("調べて回答しました")))),
                        false,
                        200));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>問い合わせ</h1>")))
                .andExpect(content().string(containsString("SC-2026-0002")))
                .andExpect(content().string(containsString("受付済み")))
                .andExpect(content().string(containsString("SC-2026-0001")))
                .andExpect(content().string(containsString("回答済み")))
                .andExpect(content().string(containsString("調べて回答しました")))
                // 次回更新日時は荷主の日時表示（日本時間と UTC のずれ）
                .andExpect(content().string(containsString("2026-11-17 14:00 Asia/Tokyo（UTC+09:00）")));
    }

    @Test
    void 行2_最新の回答がまだなければまだないと示す() throws Exception {
        given(queryService.customerList(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CustomerServiceCaseList(
                        List.of(item("SC-2026-0002", ServiceCaseStatus.RECEIVED, Optional.empty())), false, 200));

        mockMvc.perform(get(LIST)).andExpect(content().string(containsString("回答はまだありません。")));
    }

    @Test
    void 行3_問い合わせがなければないと示し表を出さない() throws Exception {
        given(queryService.customerList(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CustomerServiceCaseList(List.of(), false, 200));

        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("問い合わせはまだありません。")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<table"))));
    }

    @Test
    void 行4_問い合わせの窓口の受付時間を日本時間とUTCのずれで示す() throws Exception {
        given(queryService.customerList(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CustomerServiceCaseList(List.of(), false, 200));

        mockMvc.perform(get(LIST))
                .andExpect(content().string(containsString("受付時間")))
                .andExpect(content().string(containsString("平日 09:00〜18:00 Asia/Tokyo（UTC+09:00）")));
    }

    @Test
    void 行5_上限を超えたら新しい件数だけを示していると示す() throws Exception {
        given(queryService.customerList(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CustomerServiceCaseList(
                        List.of(item("SC-2026-0002", ServiceCaseStatus.RECEIVED, Optional.empty())), true, 200));

        mockMvc.perform(get(LIST)).andExpect(content().string(containsString("新しい 200 件だけを示しています。")));
    }
}
