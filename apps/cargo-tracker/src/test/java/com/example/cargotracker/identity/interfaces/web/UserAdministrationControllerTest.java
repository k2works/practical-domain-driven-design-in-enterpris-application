package com.example.cargotracker.identity.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
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

/** S-20 の一覧と詳細の名前の表示（Bolt 34f）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserAdministrationController.class)
@Import(UserDetailPage.class)
class UserAdministrationControllerTest {

    static final UserId TARGET = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000501"));
    static final String DETAIL = "/staff/users/" + TARGET.value();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationQueryService queryService;

    @MockitoBean
    AdministrationScopeDenialService denials;

    @Test
    void 一覧は利用状態の名前を示す() throws Exception {
        when(queryService.list(any()))
                .thenReturn(List.of(
                        new UserSummary(
                                TARGET,
                                "A 社",
                                "佐藤",
                                new EmailAddress("sato@example.com"),
                                UserStatus.ACTIVE,
                                Set.of(Role.SALES)),
                        new UserSummary(
                                new UserId(UUID.fromString("20000000-0000-0000-0000-000000000502")),
                                "A 社",
                                "鈴木",
                                new EmailAddress("suzuki@example.com"),
                                UserStatus.SUSPENDED,
                                Set.of(Role.SALES))));

        mockMvc.perform(get("/staff/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<td>利用中</td>")))
                .andExpect(content().string(Matchers.containsString("<td>利用停止</td>")));
    }

    @Test
    void 一覧に利用者を登録する入口のリンクを出す() throws Exception {
        // Bolt 38b3b の行 1
        when(queryService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/staff/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<a href=\"/staff/users/new\">利用者を登録する</a>")));
    }

    @Test
    void 詳細は企業の種類と利用状態の名前を示す() throws Exception {
        found(user(3, UserStatus.SUSPENDED));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("A 社（運用する会社）")))
                .andExpect(content().string(Matchers.containsString("<dd class=\"col-sm-9\">利用停止</dd>")))
                .andExpect(content().string(Matchers.containsString("利用再開の内容を確認する")));
    }

    @Test
    void 誤りのない詳細の群には誤りの印を付けない() throws Exception {
        // 行 3: 群があることを先に確かめてから、aria-invalid がないことを確かめる（Bolt 38）
        found(user(3, UserStatus.ACTIVE));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<fieldset id=\"roles\"")))
                .andExpect(content().string(Matchers.containsString("<fieldset id=\"suspension-reason\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("aria-invalid"))));
    }

    @Test
    void 詳細の範囲外は記録せずに404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(get(DETAIL)).andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    private void found(UserDetail user) {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.Found(user));
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
