package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationCheck;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserRegistrationService;
import com.example.cargotracker.identity.application.internal.queryservices.RegistrableCompanyLookup;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RegistrableCompany;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejectionReason;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S-20 の利用者の登録の画面（Bolt 38b3b。US-16 AC1・AC2）。企業を選ぶ画面・入力の画面・確認の領域・確定の枝と文言を、照会と登録の
 * サービスをモックにして確かめる。認可と CSRF と範囲外の記録はセキュリティの統合テストで確かめる。
 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserRegistrationController.class)
@Import(UserRegistrationPage.class)
class UserRegistrationControllerTest {

    static final String NEW = "/staff/users/new";
    static final CompanyId OPERATOR = new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
    static final CompanyId SAME_NAME = new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000002"));
    static final CompanyId SHIPPER = new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000003"));
    static final UserId REGISTERED = new UserId(UUID.fromString("30000000-0000-0000-0000-000000000101"));
    static final RegistrableCompany OPERATOR_COMPANY = new RegistrableCompany(
            OPERATOR,
            "A 社",
            CompanyKind.OPERATOR,
            Set.of(Role.CUSTOMER_SUPPORT, Role.SALES, Role.DATA_STEWARD, Role.ROUTE_DESIGNER, Role.TRACKING_MANAGER));
    static final String SUMMARY_TITLE = "利用者を登録できませんでした";
    /** WithAuthenticatedActor の運用する会社の主体の表示名（テストの準備の値）。 */
    static final String OPERATOR_NAME = "営業 一郎";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationQueryService queryService;

    @MockitoBean
    UserRegistrationService registrations;

    private void found() {
        when(queryService.registrableCompany(any(), eq(OPERATOR)))
                .thenReturn(new RegistrableCompanyLookup.Found(OPERATOR_COMPANY));
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static MockHttpServletRequestBuilder filled(MockHttpServletRequestBuilder request) {
        return request.param("companyId", OPERATOR.value().toString())
                .param("roles", Role.SALES.name())
                .param("email", "  Hanako@Example.com ")
                .param("displayName", "\u00A0新人 花子 ");
    }

    @Test
    void 企業を選ぶ画面は登録できる企業を企業名と種類で並びの順に並べる() throws Exception {
        // 行 2（同じ名前の 2 社は照会の並びのまま）
        when(queryService.registrableCompanies(any()))
                .thenReturn(List.of(
                        OPERATOR_COMPANY,
                        new RegistrableCompany(SAME_NAME, "A 社", CompanyKind.OPERATOR, Set.of(Role.SALES)),
                        new RegistrableCompany(SHIPPER, "荷主 B", CompanyKind.SHIPPER, Set.of(Role.SHIPPER))));

        String page = body(get(NEW));

        assertThat(page)
                .contains("<h1>利用者の登録</h1>")
                .contains("name=\"step\" value=\"company\"")
                .contains("次へ");
        assertThat(page.indexOf("id=\"company-" + OPERATOR.value() + "\""))
                .isPositive()
                .isLessThan(page.indexOf("id=\"company-" + SAME_NAME.value() + "\""));
        assertThat(page.indexOf("id=\"company-" + SAME_NAME.value() + "\""))
                .isLessThan(page.indexOf("id=\"company-" + SHIPPER.value() + "\""));
        assertThat(page).contains("A 社（運用する会社）").contains("荷主 B（荷主企業）");
    }

    @Test
    void 登録できる企業がなければ文言だけを示し次へを出さない() throws Exception {
        // 行 2
        when(queryService.registrableCompanies(any())).thenReturn(List.of());

        assertThat(body(get(NEW))).contains("登録できる企業がありません。").doesNotContain("次へ");
    }

    @Test
    void 企業を選ばずに次へ進むとエラー要約で選ぶよう示す() throws Exception {
        // 行 3（ラジオを選ばずに送ると companyId は送られず、印だけが送られる）
        when(queryService.registrableCompanies(any())).thenReturn(List.of(OPERATOR_COMPANY));

        String page = body(get(NEW).param("step", "company"));

        assertThat(page)
                .contains(SUMMARY_TITLE)
                .contains("href=\"#company-" + OPERATOR.value() + "\">企業: 登録する企業を 1 つ選んでください。</a>");
    }

    @Test
    void 初めて開いた企業を選ぶ画面にはエラー要約を出さない() throws Exception {
        // 行 3 の対照
        when(queryService.registrableCompanies(any())).thenReturn(List.of(OPERATOR_COMPANY));

        assertThat(body(get(NEW))).doesNotContain(SUMMARY_TITLE).doesNotContain("aria-invalid");
    }

    @Test
    void 入力の画面は企業と付ける役割を宣言の順で示しメールアドレスと表示名の欄を出す() throws Exception {
        // 行 4（運用する会社の複数の役割で順を確かめる）
        found();

        String page = body(get(NEW).param("step", "company")
                .param("companyId", OPERATOR.value().toString()));

        assertThat(page).contains("A 社（運用する会社）").contains("付ける役割");
        List<Integer> positions = List.of(
                        Role.SALES,
                        Role.ROUTE_DESIGNER,
                        Role.TRACKING_MANAGER,
                        Role.DATA_STEWARD,
                        Role.CUSTOMER_SUPPORT)
                .stream()
                .map(role -> page.indexOf("id=\"role-" + role.name() + "\""))
                .toList();
        assertThat(positions).doesNotContain(-1).isSorted();
        assertThat(page)
                .contains("id=\"email\"")
                .contains("id=\"display-name\"")
                .doesNotContain("autocomplete=\"email\"");
    }

    @Test
    void 入力の画面の企業がない範囲外か形の誤りなら404にする() throws Exception {
        // 行 5（GET では範囲外を記録しない。記録するのは登録のサービスの確定だけ）
        when(queryService.registrableCompany(any(), eq(OPERATOR)))
                .thenReturn(new RegistrableCompanyLookup.OutOfScope());
        when(queryService.registrableCompany(any(), eq(SHIPPER))).thenReturn(new RegistrableCompanyLookup.NotFound());

        mockMvc.perform(get(NEW).param("step", "company")
                        .param("companyId", OPERATOR.value().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(NEW).param("step", "company")
                        .param("companyId", SHIPPER.value().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(NEW).param("step", "company").param("companyId", "not-a-uuid"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(registrations);
    }

    @Test
    void 入力の画面の企業が無効か荷受人なら企業を選ぶ画面に戻してエラー要約を示す() throws Exception {
        // 行 5
        when(queryService.registrableCompanies(any())).thenReturn(List.of(OPERATOR_COMPANY));
        when(queryService.registrableCompany(any(), eq(SHIPPER))).thenReturn(new RegistrableCompanyLookup.Inactive());
        when(queryService.registrableCompany(any(), eq(SAME_NAME)))
                .thenReturn(new RegistrableCompanyLookup.NoAssignableRoles());

        for (CompanyId company : List.of(SHIPPER, SAME_NAME)) {
            assertThat(body(get(NEW).param("step", "company")
                            .param("companyId", company.value().toString())))
                    .contains("<h1>利用者の登録</h1>")
                    .contains("企業: 選んだ企業には利用者を登録できません。ほかの企業を選んでください。");
        }
    }

    @Test
    void 確認は保存する値で確認の領域を示しhiddenで値を持ち何も書かない() throws Exception {
        // 行 6
        found();
        when(registrations.check(any(), eq(OPERATOR), any(), any(), any()))
                .thenReturn(new UserRegistrationCheck.Acceptable());

        String page = body(filled(post(NEW + "/confirmation")));

        assertThat(page)
                .contains("<h1>利用者を登録しますか</h1>")
                .contains("<dt class=\"col-sm-3\">対象</dt>")
                .contains("<dt class=\"col-sm-3\">現在の状態</dt>")
                .contains("<dt class=\"col-sm-3\">変更内容</dt>")
                .contains("<dt class=\"col-sm-3\">影響</dt>")
                .contains("<dt class=\"col-sm-3\">実行者</dt>")
                .contains("<dt class=\"col-sm-3\">回復手段</dt>")
                .contains("A 社（運用する会社）")
                .contains("未登録")
                .contains("hanako@example.com、新人 花子、営業担当者")
                .contains("登録したメールアドレスへ、初回の設定の案内（24 時間有効）を送ります。")
                .contains("システム管理者 " + OPERATOR_NAME)
                .contains("誤って登録したときは、利用者の詳細から利用を停止してください（送った案内のリンクも使えなくなります）。")
                .contains("name=\"email\" value=\"hanako@example.com\"")
                .contains("name=\"displayName\" value=\"新人 花子\"")
                .contains("name=\"roles\" value=\"SALES\"");
        verify(registrations, never()).register(any(), any(), any(), any(), any());
    }

    @Test
    void 画面で見る誤りはまとめてエラー要約に示しサービスを呼ばない() throws Exception {
        // 行 7（形の誤ったメールアドレス、表示名の空、役割なし）
        found();

        String page = body(post(NEW + "/confirmation")
                .param("companyId", OPERATOR.value().toString())
                .param("email", "hanako.example.com")
                .param("displayName", " "));

        assertThat(page)
                .contains(SUMMARY_TITLE)
                .contains("href=\"#role-SALES\">付ける役割: 1 つ以上選んでください。</a>")
                .contains("href=\"#email\">メールアドレス: 名前@ドメイン の形で入力してください（例: user@example.com）。</a>")
                .contains("href=\"#display-name\">表示名: 入力してください。</a>")
                .contains("value=\"hanako.example.com\"");
        verifyNoInteractions(registrations);
    }

    @Test
    void メールアドレスが空なら入力するよう示す() throws Exception {
        // 行 7（空欄は形の誤りと分けて示す）
        found();

        assertThat(body(post(NEW + "/confirmation")
                        .param("companyId", OPERATOR.value().toString())
                        .param("roles", Role.SALES.name())
                        .param("displayName", "新人 花子")))
                .contains("href=\"#email\">メールアドレス: 入力してください。</a>")
                .doesNotContain("表示名: ");
        verifyNoInteractions(registrations);
    }

    @Test
    void サービスの拒否は一件を全文で示し入力を戻す() throws Exception {
        // 行 8
        found();
        when(registrations.check(any(), eq(OPERATOR), any(), eq("\u00A0新人 花子 "), any()))
                .thenReturn(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.InvalidDisplayName()))
                .thenReturn(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.DuplicateEmail()))
                .thenReturn(new UserRegistrationCheck.Refused(
                        new UserRegistrationRefusal.Rejected(new UserRegistrationRejection(
                                UserRegistrationRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.SHIPPER)))))
                .thenReturn(new UserRegistrationCheck.Refused(
                        new UserRegistrationRefusal.Rejected(new UserRegistrationRejection(
                                UserRegistrationRejectionReason.OVERSIGHT_ROLE, Set.of(Role.SYSTEM_ADMIN)))))
                .thenReturn(new UserRegistrationCheck.Refused(
                        new UserRegistrationRefusal.Rejected(new UserRegistrationRejection(
                                UserRegistrationRejectionReason.CONFLICTING_ROLES,
                                Set.of(Role.SYSTEM_ADMIN, Role.SALES)))));

        String rule = body(filled(post(NEW + "/confirmation")));
        assertThat(rule)
                .contains("href=\"#display-name\">表示名: 見分けやすい文字で、200 文字以内で入力してください。</a>")
                .contains("value=\"  Hanako@Example.com \"");
        assertThat(body(filled(post(NEW + "/confirmation"))))
                .contains("href=\"#email\">メールアドレス: 既に登録されています。ほかのメールアドレスを入力してください。</a>");
        assertThat(body(filled(post(NEW + "/confirmation")))).contains("付ける役割: 荷主担当者は、運用する会社の利用者には付けられません。</a>");
        assertThat(body(filled(post(NEW + "/confirmation")))).contains("付ける役割: システム管理者は、この画面では付けられません。</a>");
        assertThat(body(filled(post(NEW + "/confirmation"))))
                .contains("付ける役割: システム管理者・監査担当者と、業務データを変更できる役割（営業担当者）は併せて付けられません。</a>");
        // サービスの拒否は 1 件（エラー要約のリンクは 1 つ）
        assertThat(rule.split("<li>", -1)).hasSize(2);
    }

    @Test
    void 確認の判定が範囲外なら404にし権限がなければ403にする() throws Exception {
        // 行 8（確認は範囲外を記録しない。check は何も書かない）
        found();
        when(registrations.check(any(), eq(OPERATOR), any(), any(), any()))
                .thenReturn(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.CompanyOutOfScope()))
                .thenReturn(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.NotPermitted()));

        mockMvc.perform(filled(post(NEW + "/confirmation"))).andExpect(status().isNotFound());
        mockMvc.perform(filled(post(NEW + "/confirmation"))).andExpect(status().isForbidden());
        verify(registrations, never()).register(any(), any(), any(), any(), any());
    }

    @Test
    void 確認から戻ると入力を戻した入力の画面を示す() throws Exception {
        // 行 10
        found();

        String page = body(filled(post(NEW + "/form")));

        assertThat(page)
                .contains("id=\"display-name\"")
                .contains("value=\"  Hanako@Example.com \"")
                .contains("value=\"\u00A0新人 花子 \"")
                .doesNotContain(SUMMARY_TITLE)
                .containsPattern("id=\"role-SALES\"[^>]*checked");
        verifyNoInteractions(registrations);
    }

    @Test
    void 戻るの企業が範囲外なら404にする() throws Exception {
        // 行 10
        when(queryService.registrableCompany(any(), eq(OPERATOR)))
                .thenReturn(new RegistrableCompanyLookup.OutOfScope());

        mockMvc.perform(filled(post(NEW + "/form"))).andExpect(status().isNotFound());
    }

    @Test
    void 確定すると登録した利用者の詳細へ移し知らせを出し行き先にメールアドレスを入れない() throws Exception {
        // 行 11
        when(registrations.register(any(), eq(OPERATOR), eq(EmailAddress.of("hanako@example.com")), any(), any()))
                .thenReturn(new UserRegistrationOutcome.Registered(REGISTERED));

        String location = mockMvc.perform(filled(post("/staff/users")))
                .andExpect(redirectedUrl("/staff/users/" + REGISTERED.value()))
                .andExpect(flash().attribute("notice", "利用者を登録しました。登録したメールアドレスへ初回の設定の案内を送ります。"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        assertThat(location).doesNotContain("hanako").doesNotContain("%40");
    }

    @Test
    void 確定の範囲外は404で重複は入力の画面に示す() throws Exception {
        // 行 11（範囲外の試行は登録のサービスが記録する）
        found();
        when(registrations.register(any(), eq(OPERATOR), any(), any(), any()))
                .thenReturn(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.CompanyOutOfScope()))
                .thenReturn(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.DuplicateEmail()));

        mockMvc.perform(filled(post("/staff/users"))).andExpect(status().isNotFound());
        assertThat(body(filled(post("/staff/users")))).contains("メールアドレス: 既に登録されています。ほかのメールアドレスを入力してください。");
    }

    @Test
    void 確定の企業が無効なら企業を選ぶ画面に戻す() throws Exception {
        // 行 11
        when(queryService.registrableCompanies(any())).thenReturn(List.of(OPERATOR_COMPANY));
        when(registrations.register(any(), eq(OPERATOR), any(), any(), any()))
                .thenReturn(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.InactiveCompany()));

        assertThat(body(filled(post("/staff/users"))))
                .contains("<h1>利用者の登録</h1>")
                .contains("企業: 選んだ企業には利用者を登録できません。ほかの企業を選んでください。");
    }
}
