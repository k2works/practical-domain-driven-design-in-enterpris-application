package com.example.cargotracker.identity.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.application.internal.commandservices.AdministrationScopeDenialService;
import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.RoleChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserRoleChangeService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S-20 の役割の変更の拒否の枝と範囲外の試行の記録（Bolt 34f。分ける前の controller の特性のテストを分けた）。確定の範囲外は記録して 404、
 * 確かめる範囲外は記録せずに 404、権限なしは 403。詳細の部品は本物を読む（モックにすると範囲外の記録の守りが消える）。
 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserRoleChangeController.class)
@Import(UserDetailPage.class)
class UserRoleChangeControllerTest {

    static final UserId TARGET = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000501"));
    static final String DETAIL = "/staff/users/" + TARGET.value();
    static final String ROLE_CONFIRMATION = DETAIL + "/role-changes/confirmation";
    static final String STALE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationQueryService queryService;

    @MockitoBean
    UserRoleChangeService roleChanges;

    @MockitoBean
    AdministrationScopeDenialService denials;

    @Test
    void 役割の確定で詳細が範囲外なら試行を記録して404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 役割の確定の判定が範囲外なら試行を記録して404にする() throws Exception {
        found(user(3));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.OutOfScope<>()));

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 役割の確認で詳細が範囲外なら記録せずに404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(get(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 役割の確認の判定が範囲外なら記録せずに404にする() throws Exception {
        found(user(3));
        when(roleChanges.check(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeCheck.Refused(new UserAdministrationRefusal.OutOfScope<>()));

        mockMvc.perform(get(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 役割の確定の権限がなければ403にする() throws Exception {
        found(user(3));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.NotPermitted<>()));

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isForbidden());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 詳細を読む権限がなければ403にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.NotPermitted());

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 役割の確定の判定で利用者がいなければ404にする() throws Exception {
        found(user(3));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.NotFound<>()));

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 役割の確定が古い版なら最新の版と今の役割で詳細を示し直す() throws Exception {
        when(queryService.detail(any(), eq(TARGET)))
                .thenReturn(new UserLookup.Found(user(3)), new UserLookup.Found(user(4)));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "ROUTE_DESIGNER").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("役割を変更できませんでした")))
                .andExpect(content().string(Matchers.containsString(STALE)))
                .andExpect(content().string(Matchers.containsString("name=\"version\" value=\"4\"")))
                .andExpect(content().string(Matchers.containsString("value=\"SALES\" checked")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"ROUTE_DESIGNER\" checked"))))
                // 古い版は選び方の誤りではないので、表示名を付けずリンクせず、群に誤りの印を付けない（Bolt 38 の開発レビュー）
                .andExpect(content().string(summaryWithoutLink()))
                .andExpect(content().string(Matchers.not(Matchers.containsString("付ける役割: "))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("aria-invalid"))));
    }

    @Test
    void 役割の確認が規則に当たれば選んだまま文言を示す() throws Exception {
        found(user(3));
        when(roleChanges.check(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeCheck.Refused(new UserAdministrationRefusal.Rejected<>(
                        RoleChangeRejection.of(RoleChangeRejectionReason.NO_ROLES))));

        mockMvc.perform(get(ROLE_CONFIRMATION).param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("役割を変更できませんでした")))
                // 行 1: 共通部品と同じ「表示名: 文言」で、群の最初の選択肢へ向かう（Bolt 38）
                .andExpect(content()
                        .string(Matchers.containsString("<a href=\"#role-SALES\">付ける役割: 役割を 1 つ以上選んでください。</a>")))
                // 要約の中のリンクの陽性の対照（行 4 の否定が空振りしないこと）と、要約へフォーカスを移すスクリプト（V6 を速い層でも）
                .andExpect(content().string(Matchers.not(summaryWithoutLink())))
                .andExpect(content().string(Matchers.containsString("src=\"/js/transport-request-form.js\"")))
                .andExpect(content().string(containsPattern("<fieldset id=\"roles\"[^>]*aria-invalid=\"true\"")))
                .andExpect(content().string(containsPattern("<fieldset id=\"suspension-reason\"")))
                .andExpect(content()
                        .string(Matchers.not(containsPattern("<fieldset id=\"suspension-reason\"[^>]*aria-invalid"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"SALES\" checked"))));
    }

    @Test
    void 役割の確定が規則に当たれば送った版のまま文言を示す() throws Exception {
        // 読み直した利用者の版（4）でなく、画面が送った版（3）のまま示す（先の更新を上書きしない）
        when(queryService.detail(any(), eq(TARGET)))
                .thenReturn(new UserLookup.Found(user(3)), new UserLookup.Found(user(4)));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Rejected<>(
                        RoleChangeRejection.of(RoleChangeRejectionReason.NO_CHANGE))));

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("役割を変更できませんでした")))
                .andExpect(content().string(Matchers.containsString("今の役割から変わっていません。")))
                .andExpect(content().string(Matchers.containsString("name=\"version\" value=\"3\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("name=\"version\" value=\"4\""))));
    }

    @Test
    void 役割の確定で値の誤りより先に範囲外の試行を記録する() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "UNKNOWN").param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 役割の確定で数でない版より先に範囲外の試行を記録する() throws Exception {
        // 行 5: 版の解釈は範囲の判定の後（利用状態の確定と同じ。Bolt 34f の指摘 9。Bolt 38）
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "x"))
                .andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
        verifyNoInteractions(roleChanges);
    }

    @Test
    void 役割の確定で版がなくても先に範囲外の試行を記録する() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES")).andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
        verifyNoInteractions(roleChanges);
    }

    @Test
    void 役割の欄のない荷受人の企業の利用者への拒否は欄へリンクしない() throws Exception {
        // 行 4: URL から役割を送ると企業の種類の拒否になるが、示す欄がないので表示名を付けずリンクしない（Bolt 38）
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.Found(consignee(3)));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any()))
                .thenReturn(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Rejected<>(
                        new RoleChangeRejection(RoleChangeRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.SALES)))));

        mockMvc.perform(post(ROLE_CONFIRMATION).param("roles", "SALES").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"error-summary\"")))
                .andExpect(content().string(Matchers.containsString("営業担当者は、荷受人の企業の利用者には付けられません。")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("付ける役割: "))))
                .andExpect(content().string(summaryWithoutLink()))
                // 出口: 利用状態の領域と一覧へ戻るリンクは残る
                .andExpect(content().string(Matchers.containsString("id=\"status-title\"")))
                .andExpect(content().string(Matchers.containsString("利用者の一覧へ戻る")));
    }

    @Test
    void 役割の欄のない自分の詳細への選び方の誤りは欄へリンクしない() throws Exception {
        // 行 4c: 判定の順で自分は先に拒まれるので届かないが、部品の「欄を描かないならリンクしない」を確かめる（サービスを mock）
        UserId self = TestActors.STAFF_USER;
        when(queryService.detail(any(), eq(self))).thenReturn(new UserLookup.Found(user(self, 3)));
        when(roleChanges.check(any(), eq(self), anyLong(), any()))
                .thenReturn(new RoleChangeCheck.Refused(new UserAdministrationRefusal.Rejected<>(
                        RoleChangeRejection.of(RoleChangeRejectionReason.NO_ROLES))));

        mockMvc.perform(get("/staff/users/" + self.value() + "/role-changes/confirmation")
                        .param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"error-summary\"")))
                .andExpect(content().string(Matchers.containsString("役割を 1 つ以上選んでください。")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("付ける役割: "))))
                .andExpect(content().string(summaryWithoutLink()));
    }

    @Test
    void 役割を変えたら知らせとともに詳細へ移す() throws Exception {
        found(user(3));
        when(roleChanges.change(any(), eq(TARGET), anyLong(), any())).thenReturn(new RoleChangeOutcome.Changed(4));

        mockMvc.perform(post(ROLE_CONFIRMATION)
                        .param("roles", "SALES", "ROUTE_DESIGNER")
                        .param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("notice", "役割を変更しました。相手の次の操作から効きます。"));

        verify(roleChanges).change(any(), eq(TARGET), eq(3L), eq(Set.of(Role.SALES, Role.ROUTE_DESIGNER)));
    }

    private void found(UserDetail user) {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.Found(user));
    }

    private static UserDetail user(long version) {
        return user(version, UserStatus.ACTIVE);
    }

    private static UserDetail user(UserId id, long version) {
        return new UserDetail(
                id,
                new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000003")),
                "A 社",
                CompanyKind.OPERATOR,
                "営業 一郎",
                new EmailAddress("sales@example.com"),
                UserStatus.ACTIVE,
                Set.of(Role.SALES),
                version,
                Optional.empty(),
                Optional.empty());
    }

    private static UserDetail consignee(long version) {
        return new UserDetail(
                TARGET,
                new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000004")),
                "C 社",
                CompanyKind.CONSIGNEE,
                "高橋",
                new EmailAddress("takahashi@example.com"),
                UserStatus.ACTIVE,
                Set.of(),
                version,
                Optional.empty(),
                Optional.empty());
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
                version,
                Optional.empty(),
                Optional.empty());
    }

    /** 本文に正規表現に合う箇所がある（属性の並びによらず確かめる）。 */
    static Matcher<String> containsPattern(String regex) {
        return Matchers.matchesPattern(Pattern.compile("(?s).*" + regex + ".*"));
    }

    /** エラー要約の中にリンクがない（要約があることは呼ぶ側が先に確かめる）。 */
    static Matcher<String> summaryWithoutLink() {
        return Matchers.not(containsPattern("id=\"error-summary\"(?:(?!</div>).)*<a "));
    }
}
