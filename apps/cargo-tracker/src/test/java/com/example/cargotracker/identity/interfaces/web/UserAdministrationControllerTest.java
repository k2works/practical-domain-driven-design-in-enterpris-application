package com.example.cargotracker.identity.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.RequestBuilder;

/** S-20 の一覧と詳細の名前の表示（Bolt 34f）。 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserAdministrationController.class)
@Import(UserDetailPage.class)
class UserAdministrationControllerTest {

    static final UserId TARGET = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000501"));
    static final String DETAIL = "/staff/users/" + TARGET.value();
    static final String ROLE_CONFIRMATION = DETAIL + "/role-changes/confirmation";
    static final String STATUS_CONFIRMATION = DETAIL + "/status-changes/confirmation";
    static final String STALE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

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
    void 詳細は企業の種類と利用状態の名前を示す() throws Exception {
        found(user(3, UserStatus.SUSPENDED));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("A 社（運用する会社）")))
                .andExpect(content().string(Matchers.containsString("<dd class=\"col-sm-9\">利用停止</dd>")))
                .andExpect(content().string(Matchers.containsString("利用再開の内容を確認する")));
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
