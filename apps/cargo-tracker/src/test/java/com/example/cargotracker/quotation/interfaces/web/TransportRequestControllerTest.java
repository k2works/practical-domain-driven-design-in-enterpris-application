package com.example.cargotracker.quotation.interfaces.web;

import static org.hamcrest.Matchers.containsString;
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

@WebMvcTest(
        controllers = TransportRequestController.class,
        properties = {
            "cargotracker.provisional-actor.shipper-company-id=00000000-0000-0000-0000-000000000001",
            "cargotracker.provisional-actor.user-id=00000000-0000-0000-0000-000000000101"
        })
class TransportRequestControllerTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final TransportRequestId ID =
            new TransportRequestId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProvisionalActorProperties.class)
    static class Properties {}

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
    void 輸送要求を仮の主体で提出すると完了画面へリダイレクトする() throws Exception {
        SubmitTransportRequestCommand expected =
                new SubmitTransportRequestCommand(SHIPPER, USER, new Location("JPTYO"), new Location("NLRTM"));
        given(commandService.submit(expected)).willReturn(ID);

        mockMvc.perform(post("/customer/transport-requests")
                        .param("origin", "JPTYO")
                        .param("destination", "NLRTM"))
                .andExpect(redirectedUrl("/customer/transport-requests/" + ID.value() + "/submitted"));

        then(commandService).should().submit(expected);
    }

    @Test
    void 小文字と前後の空白はそろえて提出する() throws Exception {
        given(commandService.submit(any())).willReturn(ID);

        mockMvc.perform(post("/customer/transport-requests")
                        .param("origin", " jptyo ")
                        .param("destination", "nlrtm"))
                .andExpect(redirectedUrl("/customer/transport-requests/" + ID.value() + "/submitted"));

        then(commandService)
                .should()
                .submit(new SubmitTransportRequestCommand(SHIPPER, USER, new Location("JPTYO"), new Location("NLRTM")));
    }

    @Test
    void 場所の形式が誤っていれば提出せず誤りと入力値を示す() throws Exception {
        mockMvc.perform(post("/customer/transport-requests")
                        .param("origin", "TYO")
                        .param("destination", "NLRTM"))
                .andExpect(status().isOk())
                .andExpect(view().name("quotation/transport-requests/new"))
                .andExpect(model().attributeHasFieldErrors("transportRequestForm", "origin"))
                .andExpect(content().string(containsString("UN/LOCODE（国コード 2 文字 + 地点コード 3 文字、例: JPTYO）")))
                .andExpect(content().string(containsString("value=\"TYO\"")));

        then(commandService).should(never()).submit(any());
    }

    @Test
    void 目的地だけが誤っていれば目的地だけに誤りを示す() throws Exception {
        mockMvc.perform(post("/customer/transport-requests")
                        .param("origin", "JPTYO")
                        .param("destination", "NL"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("transportRequestForm", "destination"))
                .andExpect(model().attributeErrorCount("transportRequestForm", 1));

        then(commandService).should(never()).submit(any());
    }

    @Test
    void 空欄なら入力を求める() throws Exception {
        mockMvc.perform(post("/customer/transport-requests")
                        .param("origin", " ")
                        .param("destination", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeErrorCount("transportRequestForm", 2))
                .andExpect(content().string(containsString("出発地を入力してください")))
                .andExpect(content().string(containsString("目的地を入力してください")));

        then(commandService).should(never()).submit(any());
    }

    @Test
    void 提出の完了画面に見積依頼の番号と審査中と次に起きることを表示する() throws Exception {
        TransportRequestId id = ID;
        given(queryService.findById(id))
                .willReturn(Optional.of(TransportRequest.submit(
                        id,
                        SHIPPER,
                        new ShipmentTerms(new Location("JPTYO"), new Location("NLRTM")),
                        USER,
                        new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")))));

        mockMvc.perform(get("/customer/transport-requests/{id}/submitted", id.value()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(id.value().toString())))
                .andExpect(content().string(containsString("見積依頼（輸送要求）ID")))
                .andExpect(content().string(containsString("審査中")))
                .andExpect(content().string(containsString("営業担当者が内容を審査し")));
    }

    @Test
    void 存在しない輸送要求の完了画面は見つからない() throws Exception {
        given(queryService.findById(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/customer/transport-requests/{id}/submitted", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
