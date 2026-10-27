package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.cargotracker.identity.application.internal.commandservices.TotpResetCheck;
import com.example.cargotracker.identity.application.internal.commandservices.TotpResetOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UnlockCheck;
import com.example.cargotracker.identity.application.internal.commandservices.UnlockOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserLoginCredentialService;
import com.example.cargotracker.identity.application.internal.queryservices.UserAdministrationQueryService;
import com.example.cargotracker.identity.application.internal.queryservices.UserLookup;
import com.example.cargotracker.identity.domain.model.rules.IdentityVerificationPolicy.Violation;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S-20 のログインの資格（ロックの解除と管理者による TOTP の解除）の確認と確定（Bolt 38c。行 2〜5・7・8a・8b・11）。詳細の部品は本物を読む
 * （モックにすると範囲外の記録の守りが消える）。
 */
// 画面の単体テストはコントローラーの振る舞いだけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる（Bolt 14）
@WithAuthenticatedActor(Role.SYSTEM_ADMIN)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UserLoginCredentialController.class)
@Import(UserDetailPage.class)
class UserLoginCredentialControllerTest {

    static final UserId TARGET = new UserId(UUID.fromString("20000000-0000-0000-0000-000000000501"));
    static final String DETAIL = "/staff/users/" + TARGET.value();
    static final String UNLOCK = DETAIL + "/unlocks/confirmation";
    static final String TOTP_RESET = DETAIL + "/totp-resets/confirmation";
    static final UtcInstant LOCKED_UNTIL = new UtcInstant(Instant.parse("2026-11-09T01:45:00Z"));
    static final UtcInstant ENROLLED_AT = new UtcInstant(Instant.parse("2026-10-01T00:00:00.123456Z"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationQueryService queryService;

    @MockitoBean
    UserLoginCredentialService credentials;

    @MockitoBean
    AdministrationScopeDenialService denials;

    // ---- ロックの解除 ----

    @Test
    void ロックの解除の確認は確認の領域の全文を示し解除しない() throws Exception {
        found(user());
        when(credentials.checkUnlock(any(), eq(TARGET))).thenReturn(new UnlockCheck.Acceptable());

        mockMvc.perform(get(UNLOCK))
                .andExpect(status().isOk())
                .andExpect(text("ロックを解除しますか"))
                .andExpect(text("A 社 佐藤（sato@example.com）"))
                // 行 7（Bolt 38c2）: 現在の状態は「ロック中」とし、次に「解ける日時」を足す
                .andExpect(text("<dd class=\"col-sm-9\" id=\"current-state\">ロック中</dd>"))
                .andExpect(text("<dt class=\"col-sm-3\">解ける日時</dt>"))
                .andExpect(
                        text(
                                "<dd class=\"col-sm-9\" id=\"lock-until\">2026-11-09 10:45 Asia/Tokyo（UTC+09:00）（UTC 2026-11-09 01:45）</dd>"))
                .andExpect(text("ロックを解除する"))
                .andExpect(text("すぐにログインを試せます。失敗の回数は 0 に戻ります。"))
                .andExpect(text("ロックの解除は取り消せません（5 回続けて誤ると、また 15 分ロックされます）。"))
                .andExpect(text("システム管理者 営業 一郎"))
                .andExpect(text("解除する"));

        verify(credentials, never()).unlock(any(), any());
    }

    @Test
    void 確かめの後に期限が切れていれば現在の状態はなしとし解ける日時を出さない() throws Exception {
        // 開発レビュー L-7（確かめと詳細の時刻の間にロックが切れた。確定は NOT_LOCKED で拒まれる）
        found(user(Optional.empty()));
        when(credentials.checkUnlock(any(), eq(TARGET))).thenReturn(new UnlockCheck.Acceptable());

        mockMvc.perform(get(UNLOCK))
                .andExpect(status().isOk())
                .andExpect(text("<dd class=\"col-sm-9\" id=\"current-state\">なし</dd>"))
                .andExpect(content().string(not(containsString("解ける日時"))));
    }

    @Test
    void ロックを解除したら知らせとともに詳細へ移す() throws Exception {
        found(user());
        when(credentials.unlock(any(), eq(TARGET))).thenReturn(new UnlockOutcome.Unlocked());

        mockMvc.perform(post(UNLOCK))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("notice", "ロックを解除しました。"));
    }

    @Test
    void ロック中でなければ詳細のエラー要約に既に解除されていると示す() throws Exception {
        found(user());
        when(credentials.unlock(any(), eq(TARGET)))
                .thenReturn(new UnlockOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.NOT_LOCKED)));

        mockMvc.perform(post(UNLOCK))
                .andExpect(status().isOk())
                .andExpect(text("ロックを解除できませんでした"))
                .andExpect(text("この利用者のロックは、既に解除されています。"))
                .andExpect(text("利用者の詳細"));
    }

    @Test
    void ロックの解除の確定で詳細が範囲外なら試行を記録して404にする() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(UNLOCK)).andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
        verify(credentials, never()).unlock(any(), any());
    }

    @Test
    void ロックの解除の確認で範囲外なら記録せずに404にする() throws Exception {
        found(user());
        when(credentials.checkUnlock(any(), eq(TARGET)))
                .thenReturn(new UnlockCheck.Refused(new UserAdministrationRefusal.OutOfScope<>()));

        mockMvc.perform(get(UNLOCK)).andExpect(status().isNotFound());

        verify(denials, never()).recordDenied(any(), any());
    }

    // ---- TOTP の解除 ----

    @Test
    void TOTPの解除の確認は本人確認の欄と登録の時刻を持ち解除しない() throws Exception {
        found(user());
        when(credentials.checkTotpReset(any(), eq(TARGET))).thenReturn(new TotpResetCheck.Acceptable(ENROLLED_AT));

        mockMvc.perform(get(TOTP_RESET))
                .andExpect(status().isOk())
                .andExpect(text("認証アプリの登録を解除しますか"))
                .andExpect(text("認証アプリ: 登録済み"))
                .andExpect(text("認証アプリの登録と回復コードを消す"))
                .andExpect(text("この利用者のログイン中の session はすべて終わります。次のログインで認証アプリを登録し直します。本人の登録メールアドレスに、解除のお知らせのメールを送ります。"))
                .andExpect(text("本人が次のログインで登録し直します。"))
                .andExpect(text("登録の電話番号への折り返し"))
                .andExpect(text("所属企業の管理担当者への確認"))
                .andExpect(text("Asia/Tokyo（UTC+09:00）"))
                .andExpect(text("name=\"enrolledAt\" value=\"2026-10-01T00:00:00.123456Z\""))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"error-summary\""))));

        verify(credentials, never()).resetTotp(any(), any(), any(), any(), any());
    }

    @Test
    void 本人確認の手段も日時もなければ確認の画面にまとめて示し解除しない() throws Exception {
        found(user());
        when(credentials.checkTotpReset(any(), eq(TARGET))).thenReturn(new TotpResetCheck.Acceptable(ENROLLED_AT));

        mockMvc.perform(post(TOTP_RESET)
                        .param("enrolledAt", ENROLLED_AT.instant().toString()))
                .andExpect(status().isOk())
                .andExpect(text("認証アプリの登録を解除できませんでした"))
                .andExpect(text("href=\"#method-PHONE_CALLBACK\">本人確認の手段: どれか 1 つを選んでください。"))
                .andExpect(text("href=\"#verified-at\">本人確認の日時: 入力してください。"))
                .andExpect(text("aria-invalid=\"true\""));

        verify(credentials, never()).resetTotp(any(), any(), any(), any(), any());
    }

    @Test
    void 日時の形の誤りは入れた値を戻して示す() throws Exception {
        found(user());
        when(credentials.checkTotpReset(any(), eq(TARGET))).thenReturn(new TotpResetCheck.Acceptable(ENROLLED_AT));

        mockMvc.perform(reset("IN_PERSON", "2026/11/09 9:30"))
                .andExpect(status().isOk())
                .andExpect(text("本人確認の日時: 「2026-11-09 10:30」の形で、日本時間で入力してください。"))
                .andExpect(text("value=\"2026/11/09 9:30\""))
                .andExpect(text("value=\"IN_PERSON\" checked"));

        verify(credentials, never()).resetTotp(any(), any(), any(), any(), any());
    }

    @Test
    void 日時が未来か7日より前なら規則の文言を示す() throws Exception {
        found(user());
        when(credentials.checkTotpReset(any(), eq(TARGET))).thenReturn(new TotpResetCheck.Acceptable(ENROLLED_AT));
        when(credentials.verificationViolation(any()))
                .thenReturn(Optional.of(Violation.FUTURE), Optional.of(Violation.TOO_OLD));

        mockMvc.perform(reset("IN_PERSON", "2026-11-09 10:30")).andExpect(text("本人確認の日時: 今より後の日時は入力できません。"));
        mockMvc.perform(reset("IN_PERSON", "2026-11-01 10:30")).andExpect(text("本人確認の日時: 7 日以内に確認した日時を入力してください。"));

        verify(credentials, never()).resetTotp(any(), any(), any(), any(), any());
    }

    @Test
    void 本人確認がそろえば日本時間をUTCにして解除し知らせとともに詳細へ移す() throws Exception {
        found(user());
        when(credentials.verificationViolation(any())).thenReturn(Optional.empty());
        UtcInstant verifiedAt = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));
        when(credentials.resetTotp(
                        any(),
                        eq(TARGET),
                        eq(ENROLLED_AT),
                        eq(IdentityVerificationMethod.PHONE_CALLBACK),
                        eq(verifiedAt)))
                .thenReturn(new TotpResetOutcome.Reset());

        mockMvc.perform(reset("PHONE_CALLBACK", " 2026-11-09 10:30 "))
                .andExpect(redirectedUrl(DETAIL))
                .andExpect(flash().attribute("notice", "認証アプリの登録を解除しました。この利用者は次のログインで認証アプリを登録し直します。"));

        // 入力に誤りがなければ、確かめ（判定と資格の読み出し）を重ねずに確定する（拒否はサービスの確定が先に返す。開発レビュー M-3）
        verify(credentials, never()).checkTotpReset(any(), any());
    }

    @Test
    void 確定のときに規則に当たれば確認の画面に戻す() throws Exception {
        found(user());
        when(credentials.verificationViolation(any())).thenReturn(Optional.empty());
        when(credentials.resetTotp(any(), eq(TARGET), any(), any(), any()))
                .thenReturn(new TotpResetOutcome.Unverifiable(Violation.TOO_OLD));

        mockMvc.perform(reset("IN_PERSON", "2026-11-09 10:30"))
                .andExpect(status().isOk())
                .andExpect(text("本人確認の日時: 7 日以内に確認した日時を入力してください。"));
    }

    @Test
    void 確認の後に登録が変わっていれば詳細に開き直すよう示す() throws Exception {
        found(user());
        when(credentials.verificationViolation(any())).thenReturn(Optional.empty());
        when(credentials.resetTotp(any(), eq(TARGET), any(), any(), any()))
                .thenReturn(new TotpResetOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.ENROLLMENT_CHANGED)));

        mockMvc.perform(reset("IN_PERSON", "2026-11-09 10:30"))
                .andExpect(status().isOk())
                .andExpect(text("認証アプリの登録を解除できませんでした"))
                .andExpect(text("登録が変わりました。詳細を開き直してください。"))
                .andExpect(text("利用者の詳細"));
    }

    @Test
    void 既に解除されていれば入力の誤りより先に詳細に示す() throws Exception {
        found(user());
        when(credentials.checkTotpReset(any(), eq(TARGET)))
                .thenReturn(new TotpResetCheck.Refused(
                        new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.NOT_ENROLLED)));

        mockMvc.perform(post(TOTP_RESET)
                        .param("enrolledAt", ENROLLED_AT.instant().toString()))
                .andExpect(status().isOk())
                .andExpect(text("この利用者の認証アプリの登録は、既に解除されています。"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("本人確認の手段: "))));
    }

    @Test
    void TOTPの解除の確定で値の誤りより先に範囲外の試行を記録する() throws Exception {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.OutOfScope());

        mockMvc.perform(post(TOTP_RESET).param("enrolledAt", "x").param("method", "TELEPATHY"))
                .andExpect(status().isNotFound());

        verify(denials).recordDenied(any(), eq(TARGET));
    }

    @Test
    void 知らない手段と形の誤った登録の時刻は400() throws Exception {
        found(user());

        mockMvc.perform(reset("TELEPATHY", "2026-11-09 10:30")).andExpect(status().isBadRequest());
        mockMvc.perform(post(TOTP_RESET).param("enrolledAt", "x").param("method", "IN_PERSON"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(TOTP_RESET).param("method", "IN_PERSON")).andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.ResultMatcher text(String expected) {
        return content().string(Matchers.containsString(expected));
    }

    private static MockHttpServletRequestBuilder reset(String method, String verifiedAt) {
        return post(TOTP_RESET)
                .param("enrolledAt", ENROLLED_AT.instant().toString())
                .param("method", method)
                .param("verifiedAt", verifiedAt);
    }

    private void found(UserDetail user) {
        when(queryService.detail(any(), eq(TARGET))).thenReturn(new UserLookup.Found(user));
    }

    private static UserDetail user() {
        return user(Optional.of(LOCKED_UNTIL));
    }

    private static UserDetail user(Optional<UtcInstant> lockedUntil) {
        return new UserDetail(
                TARGET,
                new CompanyId(UUID.fromString("20000000-0000-0000-0000-000000000003")),
                "A 社",
                CompanyKind.OPERATOR,
                "佐藤",
                new EmailAddress("sato@example.com"),
                UserStatus.ACTIVE,
                Set.of(Role.SALES),
                3L,
                lockedUntil,
                Optional.of(ENROLLED_AT));
    }
}
