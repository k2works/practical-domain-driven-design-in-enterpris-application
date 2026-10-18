package com.example.cargotracker.identity.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.commandservices.StatusChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.StatusChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserStatusChangeService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * S-20 の利用状態の変更の拒否の枝と範囲外の試行の記録（Bolt 34f。分ける前の controller の特性のテストを分けた）。確定の範囲外は記録して
 * 404、確かめる範囲外は記録せずに 404、権限なしは 403。詳細の部品は本物を読む（モックにすると範囲外の記録の守りが消える）。
 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserStatusChangeController.class)
@Import(UserDetailPage.class)
class UserStatusChangeControllerTest {

    static final UserId TARGET = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000501"));
    static final String DETAIL = "/staff/users/" + TARGET.value();
    static final String STATUS_CONFIRMATION = DETAIL + "/status-changes/confirmation";
    static final String STALE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationQueryService queryService;

    @MockitoBean
    UserStatusChangeService statusChanges;

    @MockitoBean
    AdministrationScopeDenialService denials;

    @Test
    void 利用状態の確定で詳細が範囲外なら試行を記録して404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(suspend()).andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 利用状態の確定で値の誤りより先に範囲外の試行を記録する() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(STATUS_CONFIRMATION).param("status", "UNKNOWN")).andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 利用状態の確定の判定が範囲外なら試行を記録して404にする() throws Exception {
        found(user(3));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Refused(new UserAdministrationRefusal.OutOfScope<>()));

        mockMvc.perform(suspend()).andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 利用状態の確認で詳細が範囲外なら記録せずに404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(get(STATUS_CONFIRMATION)
                        .param("status", "SUSPENDED")
                        .param("reason", "EMERGENCY")
                        .param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 利用状態の確認の判定が範囲外なら記録せずに404にする() throws Exception {
        found(user(3));
        when(statusChanges.check(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeCheck.Refused(new UserAdministrationRefusal.OutOfScope<>()));

        mockMvc.perform(get(STATUS_CONFIRMATION)
                        .param("status", "SUSPENDED")
                        .param("reason", "EMERGENCY")
                        .param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 利用状態の確定の権限がなければ403にする() throws Exception {
        found(user(3));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Refused(new UserAdministrationRefusal.NotPermitted<>()));

        mockMvc.perform(suspend()).andExpect(status().isForbidden());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 利用状態の確定の判定で利用者がいなければ404にする() throws Exception {
        found(user(3));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Refused(new UserAdministrationRefusal.NotFound<>()));

        mockMvc.perform(suspend()).andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 利用状態の確定が古い版なら最新の版で詳細を示し直す() throws Exception {
        when(queryService.detail(any(), eq(TARGET)))
                .thenReturn(new UserLookup.Found(user(3)), new UserLookup.Found(user(4)));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));

        mockMvc.perform(suspend())
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("利用状態を変更できませんでした")))
                .andExpect(content().string(Matchers.containsString(STALE)))
                .andExpect(content().string(Matchers.containsString("name=\"version\" value=\"4\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("name=\"version\" value=\"3\""))));
    }

    @Test
    void 利用状態の確認が理由のない停止なら理由の欄へ文言を示す() throws Exception {
        found(user(3));
        when(statusChanges.check(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeCheck.Refused(
                        new UserAdministrationRefusal.Rejected<>(StatusChangeRejection.REASON_REQUIRED)));

        mockMvc.perform(get(STATUS_CONFIRMATION).param("status", "SUSPENDED").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("利用状態を変更できませんでした")))
                .andExpect(content().string(Matchers.containsString("停止の理由を選んでください。")))
                .andExpect(content().string(Matchers.containsString("href=\"#suspension-reason\"")));
    }

    @Test
    void 利用状態の確定が同じ状態なら最新の版で文言を示す() throws Exception {
        when(queryService.detail(any(), eq(TARGET)))
                .thenReturn(new UserLookup.Found(user(3)), new UserLookup.Found(user(4)));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(StatusChangeRejection.NO_CHANGE)));

        mockMvc.perform(post(STATUS_CONFIRMATION).param("status", "ACTIVE").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("利用状態は既に利用中です。最新の内容を確かめてください。")))
                .andExpect(content().string(Matchers.containsString("name=\"version\" value=\"4\"")));
    }

    @Test
    void 利用状態の確定が規則に当たれば選んだ理由と送った版のまま文言を示す() throws Exception {
        // 読み直した利用者の版（4）でなく、画面が送った版（3）のまま示す（同じ状態の拒否だけが最新の版にする）
        when(queryService.detail(any(), eq(TARGET)))
                .thenReturn(new UserLookup.Found(user(3)), new UserLookup.Found(user(4)));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(StatusChangeRejection.SELF_CHANGE)));

        mockMvc.perform(suspend())
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("利用状態を変更できませんでした")))
                .andExpect(content().string(Matchers.containsString("value=\"EMERGENCY\" checked")))
                .andExpect(content().string(Matchers.containsString("name=\"version\" value=\"3\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("name=\"version\" value=\"4\""))));
    }

    @Test
    void 利用を停止したら知らせとともに詳細へ移す() throws Exception {
        found(user(3));
        when(statusChanges.change(any(), eq(TARGET), anyLong(), any(), any()))
                .thenReturn(new StatusChangeOutcome.Changed(4));

        mockMvc.perform(suspend())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("notice", "利用を停止しました。相手の次の操作から使えなくなります。"));

        verify(statusChanges)
                .change(
                        any(),
                        eq(TARGET),
                        eq(3L),
                        eq(UserStatus.SUSPENDED),
                        eq(Optional.of(SuspensionReason.EMERGENCY)));
    }

    private void found(UserDetail user) {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.Found(user));
    }

    private static RequestBuilder suspend() {
        return post(STATUS_CONFIRMATION)
                .param("status", "SUSPENDED")
                .param("reason", "EMERGENCY")
                .param("version", "3");
    }

    private static UserDetail user(long version) {
        return user(version, UserStatus.ACTIVE);
    }

    private static UserDetail user(long version, UserStatus status) {
        return new UserDetail(
                TARGET,
                new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000003")),
                "A 社",
                CompanyKind.OPERATOR,
                "佐藤",
                new EmailAddress("sato@example.com"),
                status,
                Set.of(Role.SALES),
                version);
    }
}
