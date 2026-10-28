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
import com.example.cargotracker.identity.application.internal.commandservices.AssignmentChangeCheck;
import com.example.cargotracker.identity.application.internal.commandservices.AssignmentChangeOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserAssignmentService;
import com.example.cargotracker.identity.application.internal.queryservices.AdministrationListing;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignmentChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * S-20 の担当範囲の変更（Bolt 38e。US-16 AC1）。確認の領域の全文、拒否を詳細のエラー要約に示すこと、範囲外の試行の記録、値の形の誤りの
 * 400。詳細の部品は本物を読む（モックにすると範囲外の記録の守りが消える）。
 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserAssignmentController.class)
@Import(UserDetailPage.class)
class UserAssignmentControllerTest {

    static final UserId TARGET = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000501"));
    static final String DETAIL = "/staff/users/" + TARGET.value();
    static final String CONFIRMATION = DETAIL + "/assignment-changes/confirmation";
    static final Company NORTH = Company.of(
            new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000001")), "荷主 北", CompanyKind.SHIPPER, true);
    static final Company SOUTH = Company.of(
            new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000002")), "荷主 南", CompanyKind.SHIPPER, true);
    static final CompanyId OUTSIDE = new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000009"));
    static final String NORTH_ID = NORTH.id().value().toString();
    static final String SOUTH_ID = SOUTH.id().value().toString();
    static final String STALE = "この利用者の情報が先に更新されました。最新の内容を確かめてから、もう一度変更してください。";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationQueryService queryService;

    @MockitoBean
    UserAssignmentService assignmentChanges;

    @MockitoBean
    AdministrationScopeDenialService denials;

    private void found(Set<CompanyId> assigned) {
        UserDetail user = new UserDetail(
                        TARGET,
                        new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000003")),
                        "A 社",
                        CompanyKind.OPERATOR,
                        "佐藤",
                        new EmailAddress("sato@example.com"),
                        UserStatus.ACTIVE,
                        Set.of(Role.SALES),
                        3L,
                        Optional.empty(),
                        Optional.empty())
                .withAssignedCompanies(new AssignedCompanies(assigned));
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.Found(user));
        when(queryService.assignableCompanies(any()))
                .thenReturn(new AdministrationListing.Permitted<>(List.of(NORTH, SOUTH)));
    }

    private static ResultMatcher text(String expected) {
        return content().string(Matchers.containsString(expected));
    }

    @Test
    void 確認の領域は対象と現在の状態と変更内容と影響と回復手段と実行者の全文を示し何も書かない() throws Exception {
        // 行 3 の確認（AC-2）
        found(Set.of(NORTH.id(), OUTSIDE));
        when(assignmentChanges.check(any(), eq(TARGET), eq(3L), eq(Set.of(SOUTH.id()))))
                .thenReturn(new AssignmentChangeCheck.Acceptable());

        mockMvc.perform(get(CONFIRMATION).param("companies", SOUTH_ID).param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(text("<h1>担当範囲を変更しますか</h1>"))
                .andExpect(text("A 社 佐藤（sato@example.com）"))
                .andExpect(text("<dd class=\"col-sm-9\" id=\"current-companies\">担当企業: 荷主 北（この画面で変えられない企業 1 社）</dd>"))
                .andExpect(text(
                        "<span id=\"added-companies\">追加: 荷主 南</span>／<span id=\"removed-companies\">解除: 荷主 北</span>"))
                .andExpect(text("この利用者の担当企業が変わります。業務の画面で効くのは、今後の更新からです。"))
                .andExpect(text("もう一度変更すれば戻せます。"))
                .andExpect(text("システム管理者 営業 一郎"))
                .andExpect(text("name=\"companies\" value=\"" + SOUTH_ID + "\""))
                .andExpect(text("<button type=\"submit\" class=\"btn btn-primary\">変更する</button>"));
        verify(assignmentChanges, never()).change(any(), any(), anyLong(), any());
    }

    @Test
    void すべて解除する確認の戻るは選んだ企業が空であることを詳細に渡す() throws Exception {
        // 開発レビューの中-6。空の集合は送る項目がないので、選んだことを印で渡す
        found(Set.of(NORTH.id()));
        when(assignmentChanges.check(any(), eq(TARGET), eq(3L), eq(Set.of())))
                .thenReturn(new AssignmentChangeCheck.Acceptable());

        mockMvc.perform(get(CONFIRMATION).param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(text("<span id=\"removed-companies\">解除: 荷主 北</span>"))
                // 空のリストは項目を出さない（区切りだけが残るが、Spring は空の区切りを読み飛ばす）
                .andExpect(
                        text("href=\"/staff/users/" + TARGET.value() + "?&amp;version=3&amp;companiesChosen=true\""));
    }

    @Test
    void 担当企業も変更もなければなしと示す() throws Exception {
        found(Set.of());
        when(assignmentChanges.check(any(), eq(TARGET), eq(3L), eq(Set.of(NORTH.id()))))
                .thenReturn(new AssignmentChangeCheck.Acceptable());

        mockMvc.perform(get(CONFIRMATION).param("companies", NORTH_ID).param("version", "3"))
                .andExpect(text("id=\"current-companies\">担当企業: なし</dd>"))
                .andExpect(text("<span id=\"removed-companies\">解除: なし</span>"));
    }

    @Test
    void 変更がなければ詳細のエラー要約に欄の名前と文言を示し最初の選択肢へリンクする() throws Exception {
        // 行 7（AC-4）
        found(Set.of(NORTH.id()));
        when(assignmentChanges.check(any(), eq(TARGET), eq(3L), eq(Set.of(NORTH.id()))))
                .thenReturn(refused(AssignmentChangeRejection.NO_CHANGE));

        mockMvc.perform(get(CONFIRMATION).param("companies", NORTH_ID).param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(text("担当範囲を変更できませんでした"))
                .andExpect(text("<a href=\"#company-" + NORTH_ID + "\">担当企業: 今の割り当てから変わっていません。</a>"))
                .andExpect(text("aria-invalid=\"true\""));
    }

    @Test
    void 選べない企業を送られたら詳細を開き直すよう示す() throws Exception {
        // 行 5（AC-3）
        found(Set.of());
        when(assignmentChanges.check(any(), eq(TARGET), eq(3L), any()))
                .thenReturn(refused(AssignmentChangeRejection.UNSELECTABLE_COMPANY));

        mockMvc.perform(get(CONFIRMATION)
                        .param("companies", OUTSIDE.value().toString())
                        .param("version", "3"))
                .andExpect(text("担当企業: 選べない企業が含まれています。詳細を開き直してください。"));
    }

    @Test
    void 担当範囲のない役割の拒否はリンクせず文言だけを示す() throws Exception {
        // 行 8
        found(Set.of());
        when(assignmentChanges.check(any(), eq(TARGET), eq(3L), any()))
                .thenReturn(refused(AssignmentChangeRejection.NOT_ASSIGNMENT_SCOPED));

        mockMvc.perform(get(CONFIRMATION).param("companies", NORTH_ID).param("version", "3"))
                .andExpect(text("<li>この利用者の役割には、担当範囲がありません。</li>"));
    }

    @Test
    void 確定でほかの更新が先に入っていれば最新の内容で詳細を示し直す() throws Exception {
        // 行 9
        found(Set.of());
        when(assignmentChanges.change(any(), eq(TARGET), eq(3L), any()))
                .thenReturn(new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));

        mockMvc.perform(post(CONFIRMATION).param("companies", NORTH_ID).param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(text("担当範囲を変更できませんでした"))
                .andExpect(text(STALE));
    }

    @Test
    void 確定で変えたら詳細へ戻し知らせを渡す() throws Exception {
        // 行 3（PRG）
        found(Set.of());
        when(assignmentChanges.change(any(), eq(TARGET), eq(3L), eq(Set.of(NORTH.id(), SOUTH.id()))))
                .thenReturn(new AssignmentChangeOutcome.Changed(4L));

        mockMvc.perform(post(CONFIRMATION)
                        .param("companies", NORTH_ID, SOUTH_ID)
                        .param("version", "3"))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("notice", "担当範囲を変更しました。"));
    }

    @Test
    void 確定で詳細が範囲外なら値の誤りによらず試行を記録して404にする() throws Exception {
        // 行 10（T-211）。値は範囲の判定の後に解釈する
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(CONFIRMATION).param("companies", "not-a-uuid").param("version", "x"))
                .andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
        verifyNoInteractions(assignmentChanges);
    }

    @Test
    void 確認で詳細が範囲外なら記録せずに404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(get(CONFIRMATION).param("companies", NORTH_ID).param("version", "3"))
                .andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    @Test
    void 範囲の中の利用者でも企業IDの形の誤りと重複と数でない版は400にする() throws Exception {
        // 行 10b
        found(Set.of());

        mockMvc.perform(get(CONFIRMATION).param("companies", "not-a-uuid").param("version", "3"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CONFIRMATION).param("companies", NORTH_ID, NORTH_ID).param("version", "3"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CONFIRMATION).param("companies", NORTH_ID).param("version", "x"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignmentChanges);
    }

    private static AssignmentChangeCheck refused(AssignmentChangeRejection rejection) {
        return new AssignmentChangeCheck.Refused(new UserAdministrationRefusal.Rejected<>(rejection));
    }
}
