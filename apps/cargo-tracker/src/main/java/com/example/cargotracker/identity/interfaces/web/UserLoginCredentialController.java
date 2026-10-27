package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.commandservices.TotpResetCheck;
import com.example.cargotracker.identity.application.internal.commandservices.TotpResetOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UnlockCheck;
import com.example.cargotracker.identity.application.internal.commandservices.UnlockOutcome;
import com.example.cargotracker.identity.application.internal.commandservices.UserAdministrationRefusal;
import com.example.cargotracker.identity.application.internal.commandservices.UserLoginCredentialService;
import com.example.cargotracker.identity.domain.model.rules.IdentityVerificationPolicy.Violation;
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.ErrorMessage;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Operation;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Retry;
import com.example.cargotracker.identity.interfaces.web.UserDetailPage.Section;
import com.example.cargotracker.identity.interfaces.web.UserRegistrationPage.Summary;
import com.example.cargotracker.identity.interfaces.web.UserRegistrationPage.SummaryItem;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * S-20 のログインの資格（Bolt 38c。US-18 AC3 の運用の側・AC9 の管理者の経路）。詳細の「ログインの資格」の領域から、ロックの解除と、本人確認の
 * 手段と日時を記録した認証アプリの登録の解除を、確認の画面を経て確定する（利用状態の変更と同じ確認と確定の形。確定の POST も確認と同じ
 * URL に送る）。詳細の読み出しと拒否の共通の枝は詳細の部品 {@link UserDetailPage} が受け持つ。確定では先に詳細を読んで管理範囲を確かめて
 * から、送られた値を解釈する（範囲外の利用者への確定の試行を、値の誤りによらず記録に残す。Bolt 34d の開発レビュー、T-211）。
 */
@Controller
public class UserLoginCredentialController {

    /** ロックの解除の確認の URL（GET で確認、POST で確定）。 */
    static final String UNLOCK_URL = UserAdministrationController.USERS_URL + "/{id}/unlocks/confirmation";

    /** 認証アプリの登録の解除の確認の URL（GET で確認、POST で確定）。 */
    static final String TOTP_RESET_URL = UserAdministrationController.USERS_URL + "/{id}/totp-resets/confirmation";

    /** ロックの解除の完了の知らせ（UI 設計 S-20）。 */
    static final String UNLOCKED_NOTICE = "ロックを解除しました。";

    /** 認証アプリの登録の解除の完了の知らせ（UI 設計 S-20）。 */
    static final String TOTP_RESET_NOTICE = "認証アプリの登録を解除しました。この利用者は次のログインで認証アプリを登録し直します。";

    private static final String TOTP_RESET_VIEW = "identity/users/totp-reset-confirmation";
    private static final String METHOD_LABEL = "本人確認の手段";
    private static final String VERIFIED_AT_LABEL = "本人確認の日時";
    private static final String METHOD_REQUIRED = "どれか 1 つを選んでください。";
    private static final String VERIFIED_AT_REQUIRED = "入力してください。";
    private static final String VERIFIED_AT_FORMAT = "「2026-11-09 10:30」の形で、日本時間で入力してください。";

    private final UserLoginCredentialService credentials;
    private final UserDetailPage page;

    UserLoginCredentialController(UserLoginCredentialService credentials, UserDetailPage page) {
        this.credentials = credentials;
        this.page = page;
    }

    @GetMapping(UNLOCK_URL)
    public String confirmUnlock(@PathVariable String id, AuthenticatedActor actor, Model model) {
        UserDetail user = page.find(id, actor, false);
        return switch (credentials.checkUnlock(actor, user.id())) {
            case UnlockCheck.Acceptable _ -> showUnlock(user, actor, model);
            case UnlockCheck.Refused(var refusal) -> refuse(user.id(), actor, model, refusal, Section.UNLOCK, false);
        };
    }

    @PostMapping(UNLOCK_URL)
    public String unlock(
            @PathVariable String id, AuthenticatedActor actor, Model model, RedirectAttributes redirectAttributes) {
        UserId target = page.find(id, actor, true).id();
        return switch (credentials.unlock(actor, target)) {
            case UnlockOutcome.Unlocked _ -> completed(target, UNLOCKED_NOTICE, redirectAttributes);
            case UnlockOutcome.Refused(var refusal) -> refuse(target, actor, model, refusal, Section.UNLOCK, true);
        };
    }

    @GetMapping(TOTP_RESET_URL)
    public String confirmTotpReset(@PathVariable String id, AuthenticatedActor actor, Model model) {
        UserDetail user = page.find(id, actor, false);
        return switch (credentials.checkTotpReset(actor, user.id())) {
            case TotpResetCheck.Acceptable(var enrolledAt) ->
                showTotpReset(user, enrolledAt, Verification.EMPTY, actor, model);
            case TotpResetCheck.Refused(var refusal) ->
                refuse(user.id(), actor, model, refusal, Section.TOTP_RESET, false);
        };
    }

    /** 認証アプリの登録の解除の確定。手段と日時の誤りは確認の画面にまとめて示し、何も書かない。 */
    @PostMapping(TOTP_RESET_URL)
    @SuppressWarnings("java:S107") // 送られた 3 つの値と画面の部品をそろえて受ける
    public String resetTotp(
            @PathVariable String id,
            @RequestParam(name = "enrolledAt", required = false) String enrolledAt,
            @RequestParam(name = "method", required = false) String method,
            @RequestParam(name = "verifiedAt", required = false) String verifiedAt,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        // 範囲を先に確かめ、範囲外なら値の誤りによらず試行を記録して 404 にする（書き手は詳細の部品で、確定のときだけ）
        UserDetail user = page.find(id, actor, true);
        UtcInstant expectedEnrolledAt = enrolledAt(enrolledAt);
        Verification verification = Verification.of(
                method(method), verifiedAt == null ? "" : verifiedAt, credentials::verificationViolation);
        if (!verification.hasErrors()) {
            // 確定が判定と登録の確かめを先に行い、拒否を日時の規則より先に返す（確かめを重ねて読まない）
            return reset(user, expectedEnrolledAt, verification, actor, model, redirectAttributes);
        }
        // 入力に誤りがあっても、「既に解除」などの拒否は入力の誤りより先に示す（確認の画面に戻しても直せないため）
        return switch (credentials.checkTotpReset(actor, user.id())) {
            case TotpResetCheck.Refused(var refusal) ->
                refuse(user.id(), actor, model, refusal, Section.TOTP_RESET, true);
            case TotpResetCheck.Acceptable _ -> showTotpReset(user, expectedEnrolledAt, verification, actor, model);
        };
    }

    private String reset(
            UserDetail user,
            UtcInstant expectedEnrolledAt,
            Verification verification,
            AuthenticatedActor actor,
            Model model,
            RedirectAttributes redirectAttributes) {
        return switch (credentials.resetTotp(
                actor,
                user.id(),
                expectedEnrolledAt,
                verification.method().orElseThrow(),
                verification.verifiedAt().orElseThrow())) {
            case TotpResetOutcome.Reset _ -> completed(user.id(), TOTP_RESET_NOTICE, redirectAttributes);
            case TotpResetOutcome.Unverifiable(var violation) ->
                showTotpReset(user, expectedEnrolledAt, verification.violated(violation), actor, model);
            case TotpResetOutcome.Refused(var refusal) ->
                refuse(user.id(), actor, model, refusal, Section.TOTP_RESET, true);
        };
    }

    private static String completed(UserId target, String notice, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("notice", notice);
        return "redirect:" + UserAdministrationController.USERS_URL + "/" + target.value();
    }

    /** 受け付けなかった結果の画面。規則の拒否と「既に解除」は詳細のエラー要約に示す（ほかの枝は詳細の部品）。 */
    private String refuse(
            UserId target,
            AuthenticatedActor actor,
            Model model,
            UserAdministrationRefusal<LoginCredentialRejection> refusal,
            Section section,
            boolean recordDenial) {
        return page.refuse(
                target,
                actor,
                model,
                refusal,
                recordDenial,
                new Operation<>(
                        section,
                        (user, rejection) ->
                                new Retry(user.roles(), Optional.empty(), user.version(), errorMessage(rejection))));
    }

    private String showUnlock(UserDetail user, AuthenticatedActor actor, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("currentState", UserAdministrationLabels.lockState(user));
        model.addAttribute(
                "lockUntil",
                user.lockedUntil().map(UserAdministrationLabels::staffTime).orElse(null));
        addCommon(actor, model);
        return "identity/users/unlock-confirmation";
    }

    private String showTotpReset(
            UserDetail user, UtcInstant enrolledAt, Verification verification, AuthenticatedActor actor, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("enrolledAt", enrolledAt.instant().toString());
        model.addAttribute(
                "methodOptions",
                Arrays.stream(IdentityVerificationMethod.values())
                        .map(each -> new MethodOption(
                                each.name(),
                                UserAdministrationLabels.verificationMethod(each),
                                verification.method().equals(Optional.of(each))))
                        .toList());
        model.addAttribute("verifiedAt", verification.text());
        model.addAttribute("methodError", verification.methodError());
        model.addAttribute("verifiedAtError", verification.verifiedAtError());
        model.addAttribute("summary", verification.summary());
        addCommon(actor, model);
        return TOTP_RESET_VIEW;
    }

    private static void addCommon(AuthenticatedActor actor, Model model) {
        model.addAttribute("operator", Role.SYSTEM_ADMIN.displayName() + " " + actor.displayName());
        model.addAttribute(UserAdministrationLabels.ACTIVE_ATTRIBUTE, UserAdministrationLabels.ACTIVE);
    }

    /**
     * ログインの資格の拒否の文言（UI 設計 S-20 の表。Bolt 38c）。自分と監督の役割の拒否は詳細の案内と同じ文言。どれも選び方の誤りではない
     * （詳細に選び方の欄がない）。
     */
    static ErrorMessage errorMessage(LoginCredentialRejection rejection) {
        String text =
                switch (rejection) {
                    case SELF -> UserDetailPage.SELF_CREDENTIAL_NOTE;
                    case OVERSIGHT_ROLE -> UserDetailPage.OVERSIGHT_CREDENTIAL_NOTE;
                    case NOT_LOCKED -> "この利用者のロックは、既に解除されています。";
                    case NOT_ENROLLED -> "この利用者の認証アプリの登録は、既に解除されています。";
                    case ENROLLMENT_CHANGED -> "登録が変わりました。詳細を開き直してください。";
                };
        return new ErrorMessage(text, false);
    }

    /** 確認の画面が hidden で持つ登録の時刻（ISO-8601 の UTC）。ない・形の誤りは 400。 */
    private static UtcInstant enrolledAt(String value) {
        return IdentityVerificationFormConverter.enrolledAt(value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    /** 送られた本人確認の手段の名前。空や未指定は選んでいない、知らない名前は 400（停止の理由と同じ扱い）。 */
    private static Optional<IdentityVerificationMethod> method(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(IdentityVerificationMethod.valueOf(value));
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 本人確認の手段の選択肢。
     *
     * @param value 送る値（手段の名前）
     * @param label 画面の名前
     * @param selected 選んでいるか
     */
    public record MethodOption(String value, String label, boolean selected) {}

    /**
     * 確認の画面の本人確認の入力と、その誤り。
     *
     * @param method 選んだ手段
     * @param text 入れたままの日時
     * @param verifiedAt 解釈した日時（空欄・形の誤りなら空）
     * @param methodError 手段の誤り（なければ null）
     * @param verifiedAtError 日時の誤り（なければ null）
     */
    record Verification(
            Optional<IdentityVerificationMethod> method,
            String text,
            Optional<UtcInstant> verifiedAt,
            String methodError,
            String verifiedAtError) {

        /** 何も入れていない確認の画面。 */
        static final Verification EMPTY = new Verification(Optional.empty(), "", Optional.empty(), null, null);

        /** 送られた入力を確かめる。日時を解釈できたら、規則の範囲もここで確かめる（誤りをまとめて示すため）。 */
        static Verification of(
                Optional<IdentityVerificationMethod> method,
                String text,
                Function<UtcInstant, Optional<Violation>> violationOf) {
            Optional<UtcInstant> verifiedAt =
                    text.isBlank() ? Optional.empty() : IdentityVerificationFormConverter.verifiedAt(text);
            String verifiedAtError;
            if (text.isBlank()) {
                verifiedAtError = VERIFIED_AT_REQUIRED;
            } else if (verifiedAt.isEmpty()) {
                verifiedAtError = VERIFIED_AT_FORMAT;
            } else {
                verifiedAtError = violationOf
                        .apply(verifiedAt.get())
                        .map(Verification::violationText)
                        .orElse(null);
            }
            return new Verification(
                    method, text, verifiedAt, method.isEmpty() ? METHOD_REQUIRED : null, verifiedAtError);
        }

        /** 確定のときに規則に当たった入力。 */
        Verification violated(Violation violation) {
            return new Verification(method, text, verifiedAt, methodError, violationText(violation));
        }

        boolean hasErrors() {
            return methodError != null || verifiedAtError != null;
        }

        /** 確認の画面のエラー要約。誤りがなければ null。欄の順に「表示名: 文言」で各欄へリンクする。 */
        Summary summary() {
            List<SummaryItem> items = new ArrayList<>();
            if (methodError != null) {
                items.add(new SummaryItem(
                        "#method-" + IdentityVerificationMethod.values()[0].name(), METHOD_LABEL + ": " + methodError));
            }
            if (verifiedAtError != null) {
                items.add(new SummaryItem("#verified-at", VERIFIED_AT_LABEL + ": " + verifiedAtError));
            }
            return items.isEmpty() ? null : new Summary(Section.TOTP_RESET.title(), items);
        }

        private static String violationText(Violation violation) {
            return switch (violation) {
                case FUTURE -> "今より後の日時は入力できません。";
                case TOO_OLD -> "7 日以内に確認した日時を入力してください。";
            };
        }
    }
}
