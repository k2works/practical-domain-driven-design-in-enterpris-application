package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = KpiObservationController.class)
class KpiObservationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    KpiObservationQueryService queryService;

    @Test
    void KPI計測記録の一覧に業務番号と提出時刻をUTCオフセット付きで表示し内部のIDを出さない() throws Exception {
        UUID transportRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(queryService.findAll())
                .willReturn(List.of(KpiObservation.recordSubmission(
                        transportRequestId,
                        "TR-2026-0001",
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T13:04:05Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TR-2026-0001")))
                .andExpect(content().string(not(containsString(transportRequestId.toString()))))
                .andExpect(content().string(containsString("2026-10-05 13:04:05 +00:00")));
    }

    @Test
    void 業務番号のない古い記録は業務番号なしと表示する() throws Exception {
        given(queryService.findAll())
                .willReturn(List.of(KpiObservation.recordSubmission(
                        UUID.randomUUID(),
                        null,
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T13:04:05Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("（業務番号なし）")));
    }

    @Test
    void 記録がなければその旨を表示する() throws Exception {
        given(queryService.findAll()).willReturn(List.of());

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("まだ記録はありません。")));
    }
}
