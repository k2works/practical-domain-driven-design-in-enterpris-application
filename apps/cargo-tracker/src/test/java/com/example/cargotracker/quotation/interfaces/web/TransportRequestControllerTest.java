package com.example.cargotracker.quotation.interfaces.web;

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
import static org.hamcrest.Matchers.containsString;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransportRequestController.class, properties = {
        "cargotracker.provisional-actor.shipper-company-id=00000000-0000-0000-0000-000000000001",
        "cargotracker.provisional-actor.user-id=00000000-0000-0000-0000-000000000101"})
class TransportRequestControllerTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProvisionalActorProperties.class)
    static class Properties {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransportRequestCommandService commandService;

    @MockitoBean
    TransportRequestQueryService queryService;

    @Test
    void 見積依頼の作成画面を表示する() throws Exception {
        mockMvc.perform(get("/customer/transport-requests/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(content().string(containsString("出発地")))
                .andExpect(content().string(containsString("目的地")));
    }

    @Test
    void 輸送要求を提出すると完了画面へリダイレクトする() throws Exception {
        TransportRequestId id = new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        given(commandService.submit(new SubmitTransportRequestCommand(SHIPPER, USER, new Location("JPTYO"),
                new Location("NLRTM")))).willReturn(id);

        mockMvc.perform(post("/customer/transport-requests").param("origin", "JPTYO").param("destination", "NLRTM"))
                .andExpect(redirectedUrl("/customer/transport-requests/" + id.value() + "/submitted"));
    }

    @Test
    void 場所の形式が誤っていれば提出せず誤りを示す() throws Exception {
        mockMvc.perform(post("/customer/transport-requests").param("origin", "TYO").param("destination", "NLRTM"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(model().attributeHasFieldErrors("transportRequestForm", "origin"))
                .andExpect(content().string(containsString("UN/LOCODE")));

        then(commandService).should(never()).submit(any());
    }

    @Test
    void 提出の完了画面に輸送要求IDと審査中を表示する() throws Exception {
        TransportRequestId id = new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        given(queryService.findById(id)).willReturn(Optional.of(TransportRequest.submit(id, SHIPPER,
                new ShipmentTerms(new Location("JPTYO"), new Location("NLRTM")), USER,
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")))));

        mockMvc.perform(get("/customer/transport-requests/{id}/submitted", id.value()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(id.value().toString())))
                .andExpect(content().string(containsString("審査中")));
    }

    @Test
    void 存在しない輸送要求の完了画面は見つからない() throws Exception {
        given(queryService.findById(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/customer/transport-requests/{id}/submitted", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
