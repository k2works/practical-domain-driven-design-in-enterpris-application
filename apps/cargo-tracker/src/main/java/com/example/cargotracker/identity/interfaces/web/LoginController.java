package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.TotpEnrollmentQueryService;
import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordVerifiedUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ログイン（A-01）・認証コードの入力（A-02）・再認証の案内（A-03）の画面（US-18）。A-07 認証アプリの登録は
 * {@link TotpEnrollmentController}（Bolt 31b）。ログインと認証コードの
 * 送信、ログアウトは Spring Security が受ける。A-02 と登録の案内は password の要素だけで開ける（Bolt 31）。
 * ログインに失敗したときは、入れたメールアドレスを残し password を消す（UI 設計「A-01」）。
 * 開発環境（dev）だけ、開発用の利用者で入力済みにし、荷主担当者・営業担当者の開発用の利用者を選んでそのままログインできる
 * ボタンを出す（2026-10-06 の人の決定）。
 */
@Controller
public class LoginController {

    /** 入力欄に出すメールアドレスと password のモデルの名前。 */
    static final String USERNAME = "username";

    static final String PASSWORD = "password";

    /** 開発環境で選んでそのままログインできる開発用の利用者のモデルの名前。 */
    static final String DEV_ACCOUNTS = "devAccounts";

    /** dev プロファイルでだけある。ほかの環境では空で、画面は入力済みにならない（守りの 1 層目）。 */
    private final ObjectProvider<DevLoginPrefill> devLogin;

    /** dev プロファイルでだけある A-02 の入力済みのコード（Bolt 31）。ほかの環境では空で、A-02 は入力済みにならない。 */
    private final ObjectProvider<DevTotpCodeSource> devTotpCodes;

    private final TotpEnrollmentQueryService enrollment;

    public LoginController(
            ObjectProvider<DevLoginPrefill> devLogin,
            ObjectProvider<DevTotpCodeSource> devTotpCodes,
            TotpEnrollmentQueryService enrollment) {
        this.devLogin = devLogin;
        this.devTotpCodes = devTotpCodes;
        this.enrollment = enrollment;
    }

    @GetMapping("/login")
    public String login(Model model) {
        // ロックの回数と長さは LockPolicy から描く（A-01 の文言。Bolt 32 の既知の課題を Bolt 33c で直した）
        model.addAttribute("lockFailures", LockPolicy.MAX_CONSECUTIVE_FAILURES);
        model.addAttribute("lockMinutes", LockPolicy.LOCK_DURATION.toMinutes());
        devLogin.ifAvailable(prefill -> {
            if (!model.containsAttribute(USERNAME)) {
                model.addAttribute(USERNAME, prefill.email());
                model.addAttribute(PASSWORD, prefill.password());
            }
            model.addAttribute(DEV_ACCOUNTS, prefill.accounts());
        });
        return "identity/login";
    }

    /** A-02 認証コードの入力（Bolt 31）。TOTP を登録していない利用者は登録の案内へ移す。 */
    @GetMapping("/login/totp")
    public String totp(PasswordVerifiedUser user, HttpSession session, Model model) {
        if (!enrollment.isEnrolled(user.userId())) {
            return "redirect:/login/totp/enroll";
        }
        // 登録の直後（登録を始めた時刻が残っている）だけ案内を出す。TOTP・回復コードが通ると時刻は消える（Bolt 31c）
        model.addAttribute("justEnrolled", session.getAttribute(SessionKeys.ENROLLMENT_STARTED_AT) != null);
        devTotpCodes.ifAvailable(
                codes -> codes.codeFor(user.email().value()).ifPresent(code -> model.addAttribute("code", code)));
        return "identity/login-totp";
    }

    @GetMapping(SessionPaths.EXPIRED_URL)
    public String sessionExpired() {
        return "identity/session-expired";
    }

    /** コードの欄の桁数（{@code pattern}・{@code maxlength}）。テンプレートは静的なクラスを参照しないのでモデルで渡す（Bolt 33d）。 */
    @ModelAttribute("codeDigits")
    int codeDigits() {
        return TotpCredential.DIGITS;
    }
}
