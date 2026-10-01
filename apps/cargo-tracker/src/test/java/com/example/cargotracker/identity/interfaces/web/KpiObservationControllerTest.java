package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = KpiObservationController.class)
class KpiObservationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    KpiObservationQueryService queryService;

    @Test
    void KPI計測記録の一覧に輸送要求IDと提出時刻をUTCオフセット付きで表示する() throws Exception {
        UUID transportRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(queryService.findAll()).willReturn(List.of(KpiObservation.recordSubmission(transportRequestId,
                new CompanyId(UUID.randomUUID()), new UtcInstant(Instant.parse("2026-10-05T13:04:05Z")))));

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(transportRequestId.toString())))
                .andExpect(content().string(containsString("2026-10-05 13:04:05 +00:00")));
    }

    @Test
    void 記録がなければその旨を表示する() throws Exception {
        given(queryService.findAll()).willReturn(List.of());

        mockMvc.perform(get("/staff/kpi-observations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("まだ記録はありません。")));
    }
}
