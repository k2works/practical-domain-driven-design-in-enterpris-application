package com.example.cargotracker.tracking.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RecordInquiryCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.InquiryRecordingService;
import com.example.cargotracker.tracking.application.internal.commandservices.RecordInquiryOutcome;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** S-23 照会の記録（カスタマーサポート。Bolt 40-15）。認可はセキュリティの統合テストで確かめる。 */
@WithAuthenticatedActor(Role.CUSTOMER_SUPPORT)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = InquiryRecordController.class)
@Import(InquiryRecordControllerTest.FixedClock.class)
class InquiryRecordControllerTest {

    private static final String URL = "/staff/inquiries";
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InquiryRecordingService service;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder valid() {
        return post(URL)
                .param("channel", "PHONE")
                .param("trackingNumber", "ctabcdefgh2345")
                .param("inquirerKind", "SHIPPER")
                .param("content", "貨物の所在が分からない")
                .param("department", "CS");
    }

    private static RecordInquiryOutcome.Recorded recorded(Optional<CaseNumber> number, boolean existing) {
        return new RecordInquiryOutcome.Recorded(
                UUID.randomUUID(),
                number,
                existing,
                number.isPresent() && !existing
                        ? Optional.of(new UtcInstant(Instant.parse("2026-11-17T05:00:00Z")))
                        : Optional.empty(),
                number.isPresent() && !existing
                        ? Optional.of(new UtcInstant(Instant.parse("2026-11-16T05:00:00Z")))
                        : Optional.empty());
    }

    @Test
    void 行1_画面に経路と照会者の種別の選択肢と引き継ぎのチェックを示す() throws Exception {
        mockMvc.perform(get(URL + "/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("電話")))
                .andExpect(content().string(containsString("メール")))
                .andExpect(content().string(containsString("荷主")))
                .andExpect(content().string(containsString("荷受人")))
                .andExpect(content().string(containsString("有人案件へ引き継ぐ")))
                .andExpect(content().string(not(containsString("checked"))));
    }

    @Test
    void 行2_必須の入力が空ならエラー要約を示しサービスを呼ばない() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("経路: 選択肢から選んでください")))
                .andExpect(content().string(containsString("追跡番号: 入力してください")))
                .andExpect(content().string(containsString("照会者の種別: 選択肢から選んでください")));
        then(service).should(never()).record(any(), any());
    }

    @Test
    void 行3_受付時刻の形式の誤りと今より後の時刻は誤りにする() throws Exception {
        mockMvc.perform(valid().param("receivedAt", "昨日"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("受付時刻: 2026-11-16 10:30 の形（日本時間）で入力してください")));
        mockMvc.perform(valid().param("receivedAt", "2026-11-17 10:00"))
                .andExpect(content().string(containsString("受付時刻: 今より後の時刻は入れられません")));
        then(service).should(never()).record(any(), any());
    }

    @Test
    void 行4_引き継ぎなしの記録はコマンドに変換して渡し_PRGで結果を示す() throws Exception {
        given(service.record(any(), any())).willReturn(recorded(Optional.empty(), false));

        mockMvc.perform(valid())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(URL + "/new"))
                .andExpect(flash().attribute("result", "照会を記録しました。"));

        ArgumentCaptor<RecordInquiryCommand> captor = ArgumentCaptor.forClass(RecordInquiryCommand.class);
        then(service).should().record(any(), captor.capture());
        RecordInquiryCommand command = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.channel()).isEqualTo(InquiryChannel.PHONE);
        org.assertj.core.api.Assertions.assertThat(command.trackingNumber())
                .isEqualTo(new TrackingNumber("CTABCDEFGH2345"));
        org.assertj.core.api.Assertions.assertThat(command.inquirerKind()).isEqualTo(InquirerKind.SHIPPER);
        org.assertj.core.api.Assertions.assertThat(command.handOff()).isFalse();
        org.assertj.core.api.Assertions.assertThat(command.receivedAt()).isEmpty();
    }

    @Test
    void 行5_引き継いだ結果に受付番号と期限と有人案件への導線を示す() throws Exception {
        given(service.record(any(), any()))
                .willReturn(recorded(Optional.of(new CaseNumber("SC-2026-0057")), false))
                .willReturn(recorded(Optional.of(new CaseNumber("SC-2026-0058")), true));

        mockMvc.perform(valid().param("handOff", "true"))
                .andExpect(redirectedUrl(URL + "/new"))
                .andExpect(flash().attribute("resultLinkHref", "/staff/service-cases/SC-2026-0057"))
                .andExpect(flash().attribute("resultLinkLabel", "有人案件 SC-2026-0057 を開く"))
                .andExpect(flash().attributeExists("result"));
        mockMvc.perform(valid().param("handOff", "true"))
                .andExpect(flash().attribute("result", "照会を記録しました。未終結の同じ内容の有人案件 SC-2026-0058 があるため、その案件に引き継ぎました。"));
    }

    @Test
    void 行6_追跡記録がない_範囲の外は追跡番号の誤りにして入力を残す() throws Exception {
        given(service.record(any(), any())).willReturn(new RecordInquiryOutcome.TrackingNotFound());

        mockMvc.perform(valid())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("追跡番号: この追跡番号の追跡記録は見つかりません")))
                .andExpect(content().string(containsString("貨物の所在が分からない")));
    }

    @Test
    void 行7_引き継ぐのに内容が空なら照会内容の誤りにする() throws Exception {
        given(service.record(any(), any())).willReturn(new RecordInquiryOutcome.Incomplete(Set.of(MissingItem.CAUSE)));

        mockMvc.perform(valid().param("handOff", "true").param("content", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("照会内容: 引き継ぐときは入力してください")));
    }

    @Test
    void 行8_営業カレンダーが未登録なら警告を示し入力を残す() throws Exception {
        given(service.record(any(), any())).willReturn(new RecordInquiryOutcome.CalendarNotRegistered());

        mockMvc.perform(valid().param("handOff", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("営業カレンダーが登録されていないため、有人案件へ引き継げません。")));
    }

    @Test
    void 行9_権限なしは403() throws Exception {
        given(service.record(any(), any())).willReturn(new RecordInquiryOutcome.NotPermitted());

        mockMvc.perform(valid()).andExpect(status().isForbidden());
    }
}
