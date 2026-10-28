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
import com.example.cargotracker.identity.application.internal.queryservices.AdministrationListing;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

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

    @BeforeEach
    void 担当企業にできる企業は既定でなし() {
        // Bolt 38e。一覧系の照会は結果を値で返すので、モックの既定（null）にしない
        when(queryService.assignableCompanies(any())).thenReturn(new AdministrationListing.Permitted<>(List.of()));
    }

    @MockitoBean
    AdministrationScopeDenialService denials;

    @Test
    void 一覧は利用状態の名前を示す() throws Exception {
        when(queryService.list(any()))
                .thenReturn(permitted(
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
        when(queryService.list(any())).thenReturn(permitted());

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

    // ---- ログインの資格の領域（Bolt 38c。行 1） ----

    @Test
    void ロック中で登録済みの利用者には期限と2つの解除の操作を示す() throws Exception {
        found(credentialsOf(
                TARGET,
                Set.of(Role.SALES),
                Optional.of(new UtcInstant(Instant.parse("2026-11-09T01:45:00Z"))),
                Optional.of(new UtcInstant(Instant.parse("2026-10-01T00:00:00Z")))));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(text("ログインの資格"))
                // 行 7（Bolt 38c2）: ロックの期限は「解ける日時」の項目に分ける
                .andExpect(text(">ロック: ロック中</p>"))
                .andExpect(text(">解ける日時: 2026-11-09 10:45 Asia/Tokyo（UTC+09:00）（UTC 2026-11-09 01:45）</p>"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(" まで）"))))
                .andExpect(text("認証アプリ: 登録済み"))
                .andExpect(text("ロックを解除する"))
                .andExpect(text("認証アプリの登録を解除する"))
                .andExpect(text("/unlocks/confirmation"))
                .andExpect(text("/totp-resets/confirmation"));
    }

    @Test
    void ロックも登録もない停止中の利用者には状態だけを示し操作を出さない() throws Exception {
        found(user(3, UserStatus.SUSPENDED));

        mockMvc.perform(get(DETAIL))
                .andExpect(text("ロック: なし"))
                // 行 8（Bolt 38c2。V6）
                .andExpect(content().string(Matchers.not(Matchers.containsString("解ける日時"))))
                .andExpect(text("認証アプリ: 未登録"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("ロックを解除する"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("認証アプリの登録を解除する"))));
    }

    @Test
    void 停止中の利用者にも当てはまる解除の操作を出す() throws Exception {
        UserDetail enrolled =
                credentialsOf(TARGET, Set.of(Role.SALES), Optional.empty(), Optional.of(new UtcInstant(Instant.EPOCH)));
        found(new UserDetail(
                enrolled.id(),
                enrolled.companyId(),
                enrolled.companyName(),
                enrolled.companyKind(),
                enrolled.displayName(),
                enrolled.email(),
                UserStatus.SUSPENDED,
                enrolled.roles(),
                enrolled.version(),
                enrolled.lockedUntil(),
                enrolled.totpEnrolledAt()));

        mockMvc.perform(get(DETAIL)).andExpect(text("認証アプリの登録を解除する"));
    }

    @Test
    void 自分と監督の役割の利用者のログインの資格には案内だけを示す() throws Exception {
        Optional<UtcInstant> locked = Optional.of(new UtcInstant(Instant.parse("2026-11-09T01:45:00Z")));
        Optional<UtcInstant> enrolled = Optional.of(new UtcInstant(Instant.EPOCH));
        UserId self = TestActors.STAFF_USER;
        when(queryService.detail(any(), eq(self)))
                .thenReturn(new UserLookup.Found(credentialsOf(self, Set.of(Role.SYSTEM_ADMIN), locked, enrolled)));
        found(credentialsOf(TARGET, Set.of(Role.AUDITOR), locked, enrolled));

        mockMvc.perform(get("/staff/users/" + self.value()))
                .andExpect(text("自分のログインの資格は、この画面では変えられません。"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("ロックを解除する"))));
        mockMvc.perform(get(DETAIL))
                .andExpect(text("システム管理者・監査担当者のログインの資格は、この画面では変えられません。運用の手順に従ってください。"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("ロックを解除する"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("認証アプリの登録を解除する"))));
    }

    @Test
    void 荷受人の企業の利用者にも当てはまる解除の操作を出す() throws Exception {
        UserDetail operatorUser = credentialsOf(
                TARGET,
                Set.of(),
                Optional.of(new UtcInstant(Instant.parse("2026-11-09T01:45:00Z"))),
                Optional.of(new UtcInstant(Instant.EPOCH)));
        found(new UserDetail(
                operatorUser.id(),
                new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000004")),
                "C 社",
                CompanyKind.CONSIGNEE,
                operatorUser.displayName(),
                operatorUser.email(),
                operatorUser.status(),
                operatorUser.roles(),
                operatorUser.version(),
                operatorUser.lockedUntil(),
                operatorUser.totpEnrolledAt()));

        mockMvc.perform(get(DETAIL)).andExpect(text("ロックを解除する")).andExpect(text("認証アプリの登録を解除する"));
    }

    // ---- 利用状態の領域の停止の記録（Bolt 38c2。US-16 AC5、#50） ----

    static final UserId SUSPENDED_BY = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000509"));

    private static SuspensionRecord suspension(Optional<String> actorDisplayName) {
        return new SuspensionRecord(
                SuspensionReason.TRANSFERRED,
                SUSPENDED_BY,
                actorDisplayName,
                new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")));
    }

    private static UserDetail suspended(Set<Role> roles, Optional<SuspensionRecord> latest) {
        UserDetail user = credentialsOf(TARGET, roles, Optional.empty(), Optional.empty());
        return new UserDetail(
                        user.id(),
                        user.companyId(),
                        user.companyName(),
                        user.companyKind(),
                        user.displayName(),
                        user.email(),
                        UserStatus.SUSPENDED,
                        user.roles(),
                        user.version(),
                        user.lockedUntil(),
                        user.totpEnrolledAt())
                .withSuspension(latest);
    }

    @Test
    void 停止中の利用者には停止の理由と停止した人と停止した日時を示す() throws Exception {
        // 行 1
        found(suspended(Set.of(Role.SALES), Optional.of(suspension(Optional.of("管理 次郎")))));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(text("<p id=\"suspension-record-reason\">停止の理由: 異動</p>"))
                .andExpect(text("<p id=\"suspension-record-actor\">停止した人: 管理 次郎</p>"))
                .andExpect(
                        text(
                                "<p id=\"suspension-record-at\">停止した日時: 2026-11-09 10:30 Asia/Tokyo（UTC+09:00）（UTC 2026-11-09 01:30）</p>"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("記録がありません"))))
                .andExpect(text("利用再開の内容を確認する"));
    }

    @Test
    void 停止中で記録がなければ記録がないとだけ示す() throws Exception {
        // 行 2（DB を直接書き換えた停止。V2）
        found(suspended(Set.of(Role.SALES), Optional.empty()));

        mockMvc.perform(get(DETAIL))
                .andExpect(text("<p id=\"suspension-record-missing\">停止の記録: 記録がありません</p>"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("停止した人"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("停止した日時"))));
    }

    @Test
    void 停止した人の行がなければ不明と利用者IDを示す() throws Exception {
        // 行 6（開始準備の決定 (12)）
        found(suspended(Set.of(Role.SALES), Optional.of(suspension(Optional.empty()))));

        mockMvc.perform(get(DETAIL))
                .andExpect(
                        text(
                                "<p id=\"suspension-record-actor\">停止した人: 不明（利用者 ID: 20000000-0000-0000-0000-000000000509）</p>"));
    }

    @Test
    void 利用中の利用者には停止の記録を示さない() throws Exception {
        // 行 3 の画面の側
        found(user(3, UserStatus.ACTIVE));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("suspension-record"))));
    }

    @Test
    void DBで停止した監督の役割の利用者には案内と停止の記録の両方を示す() throws Exception {
        // 行 9c
        found(suspended(Set.of(Role.AUDITOR), Optional.empty()));

        mockMvc.perform(get(DETAIL))
                .andExpect(text("システム管理者・監査担当者の利用停止と再開は、この画面では行いません。"))
                .andExpect(text("停止の記録: 記録がありません"));
    }

    // ---- 担当範囲の領域（Bolt 38e。行 1・2、AC-1） ----

    static final Company NORTH = Company.of(
            new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000001")), "荷主 北", CompanyKind.SHIPPER, true);
    static final Company SOUTH = Company.of(
            new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000002")), "荷主 南", CompanyKind.SHIPPER, true);
    static final CompanyId OUTSIDE = new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000009"));

    private static UserDetail assignedTo(Set<Role> roles, UserStatus status, Set<CompanyId> assigned) {
        UserDetail user = credentialsOf(TARGET, roles, Optional.empty(), Optional.empty());
        return new UserDetail(
                        user.id(),
                        user.companyId(),
                        user.companyName(),
                        user.companyKind(),
                        user.displayName(),
                        user.email(),
                        status,
                        user.roles(),
                        user.version(),
                        user.lockedUntil(),
                        user.totpEnrolledAt())
                .withAssignedCompanies(new AssignedCompanies(assigned));
    }

    @Test
    void 担当範囲のある役割の利用者には選べる荷主の選択肢と今の担当企業の印と確認の操作を示す() throws Exception {
        // 行 1（AC-1）
        found(assignedTo(Set.of(Role.SALES), UserStatus.ACTIVE, Set.of(NORTH.id())));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH, SOUTH));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(text("<h2 id=\"assignments-title\" class=\"h5\">担当範囲</h2>"))
                .andExpect(text("<legend class=\"h6\">担当企業</legend>"))
                .andExpect(
                        text(
                                "id=\"company-30000000-0000-0000-0000-000000000001\" value=\"30000000-0000-0000-0000-000000000001\" checked=\"checked\">"))
                .andExpect(
                        text(
                                "id=\"company-30000000-0000-0000-0000-000000000002\" value=\"30000000-0000-0000-0000-000000000002\">"))
                .andExpect(text(">荷主 北</label>"))
                .andExpect(text(">荷主 南</label>"))
                .andExpect(text("/staff/users/" + TARGET.value() + "/assignment-changes/confirmation"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("assignments-none-note"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("assignments-outside-note"))));
    }

    @Test
    void 担当企業がなければまだないと案内する() throws Exception {
        found(assignedTo(Set.of(Role.CUSTOMER_SUPPORT), UserStatus.ACTIVE, Set.of()));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH, SOUTH));

        mockMvc.perform(get(DETAIL))
                .andExpect(
                        text(
                                "<p id=\"assignments-none-note\">担当企業はまだありません。担当企業は、業務の画面で扱える企業を絞るために使います（業務の画面で効くのは、今後の更新からです）。</p>"));
    }

    @Test
    void 管理範囲の外の担当企業は数だけを示す() throws Exception {
        // 決定 (11)。名前は出さない
        found(assignedTo(Set.of(Role.ROUTE_DESIGNER), UserStatus.ACTIVE, Set.of(NORTH.id(), OUTSIDE)));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH, SOUTH));

        mockMvc.perform(get(DETAIL))
                .andExpect(
                        text("<p id=\"assignments-outside-note\" class=\"form-text\">この画面で変えられない企業 1 社も担当しています。</p>"))
                .andExpect(content()
                        .string(Matchers.not(
                                Matchers.containsString(OUTSIDE.value().toString()))));
    }

    @Test
    void 無効になった荷主を担当していれば変えられない企業の数に入れる() throws Exception {
        // 開発レビューの中-1。管理範囲の中でも選べない企業（無効な荷主）は、選択肢に出さず数だけを示す
        CompanyId inactive = new CompanyId(UUID.fromString("30000000-0000-0000-0000-000000000003"));
        found(assignedTo(Set.of(Role.SALES), UserStatus.ACTIVE, Set.of(NORTH.id(), inactive)));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH, SOUTH));

        mockMvc.perform(get(DETAIL))
                .andExpect(
                        text("<p id=\"assignments-outside-note\" class=\"form-text\">この画面で変えられない企業 1 社も担当しています。</p>"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("company-" + inactive.value()))));
    }

    @Test
    void 担当範囲の確認のボタンは役割のボタンと名前で見分けられる() throws Exception {
        // 開発レビューの中-5。同じ名前のボタンが 2 つあると、読み上げのボタンの一覧で区別できない
        found(assignedTo(Set.of(Role.SALES), UserStatus.ACTIVE, Set.of()));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH));

        mockMvc.perform(get(DETAIL))
                .andExpect(text("<button type=\"submit\" class=\"btn btn-primary mt-3\">担当範囲の変更内容を確認する</button>"));
    }

    @Test
    void 選べる荷主がなければ理由を一文で示し選択肢も確認の操作も出さない() throws Exception {
        // 開発レビューの低-1。ほかの領域の前例（操作できないときはボタンを出さず理由を示す）にそろえる
        found(assignedTo(Set.of(Role.SALES), UserStatus.ACTIVE, Set.of(OUTSIDE)));
        when(queryService.assignableCompanies(any())).thenReturn(permitted());

        mockMvc.perform(get(DETAIL))
                .andExpect(text("<p id=\"assignments-unselectable-note\">あなたの管理範囲には、担当企業にできる荷主の企業がありません。</p>"))
                .andExpect(text("この画面で変えられない企業 1 社も担当しています。"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("assignment-changes"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("担当範囲の変更内容を確認する"))));
    }

    @Test
    void すべて解除して確認から戻ると担当企業の印は付かない() throws Exception {
        // 開発レビューの中-6。選んだ企業が空のときも、選んだまま（空のまま）詳細に戻る
        found(assignedTo(Set.of(Role.SALES), UserStatus.ACTIVE, Set.of(NORTH.id())));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH, SOUTH));

        mockMvc.perform(get(DETAIL).param("companiesChosen", "true").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(
                        text(
                                "id=\"company-30000000-0000-0000-0000-000000000001\" value=\"30000000-0000-0000-0000-000000000001\">"));
    }

    @Test
    void 停止中の業務担当者にも担当範囲の領域を示す() throws Exception {
        found(assignedTo(Set.of(Role.TRACKING_MANAGER), UserStatus.SUSPENDED, Set.of()));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH));

        mockMvc.perform(get(DETAIL)).andExpect(text("<h2 id=\"assignments-title\" class=\"h5\">担当範囲</h2>"));
    }

    @ParameterizedTest(name = "{0} だけの利用者には担当範囲の領域を出さない")
    @EnumSource(
            value = Role.class,
            names = {"SHIPPER", "DATA_STEWARD", "SYSTEM_ADMIN", "AUDITOR"})
    void 担当範囲のない役割の利用者には担当範囲の領域を出さない(Role unscoped) throws Exception {
        // 行 2（V3）
        found(assignedTo(Set.of(unscoped), UserStatus.ACTIVE, Set.of()));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH));

        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("assignments-title"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("assignment-changes"))));
    }

    @Test
    void 確認から戻ると選んだ担当企業が残る() throws Exception {
        found(assignedTo(Set.of(Role.SALES), UserStatus.ACTIVE, Set.of()));
        when(queryService.assignableCompanies(any())).thenReturn(permitted(NORTH, SOUTH));

        mockMvc.perform(get(DETAIL)
                        .param("companies", SOUTH.id().value().toString())
                        .param("version", "3"))
                .andExpect(
                        text(
                                "id=\"company-30000000-0000-0000-0000-000000000002\" value=\"30000000-0000-0000-0000-000000000002\" checked=\"checked\">"));
    }

    @SafeVarargs
    private static <T> AdministrationListing<T> permitted(T... items) {
        return new AdministrationListing.Permitted<>(List.of(items));
    }

    private static ResultMatcher text(String expected) {
        return content().string(Matchers.containsString(expected));
    }

    private static UserDetail credentialsOf(
            UserId id, Set<Role> roles, Optional<UtcInstant> lockedUntil, Optional<UtcInstant> enrolledAt) {
        return new UserDetail(
                id,
                new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000003")),
                "A 社",
                CompanyKind.OPERATOR,
                "佐藤",
                new EmailAddress("sato@example.com"),
                UserStatus.ACTIVE,
                roles,
                3L,
                lockedUntil,
                enrolledAt);
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
                version,
                Optional.empty(),
                Optional.empty());
    }
}
