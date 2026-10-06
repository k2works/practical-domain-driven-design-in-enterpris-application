package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/**
 * ログインの失敗の後、A-01 に「メールアドレスまたは password が正しくありません。」を示し、入れたメールアドレスだけを残す
 * （UI 設計「A-01」、AC2）。password は残さない。失敗の理由は画面に出さない（監査記録にだけ残す）。
 * メールアドレスはリダイレクトの後の 1 回だけ使うフラッシュ属性で渡す（URL に載せない）。
 */
public class KeepEmailAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** 失敗の後の行き先。 */
    static final String FAILURE_URL = "/login?error";

    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        FlashMap flash = new FlashMap();
        flash.put("username", truncated(request.getParameter("username")));
        flash.setTargetRequestPath(request.getContextPath() + "/login");
        flashMapManager.saveOutputFlashMap(flash, request, response);
        response.sendRedirect(request.getContextPath() + FAILURE_URL);
    }

    /** session に置く値を、メールアドレスの上限の長さで切る（大きな入力で session を膨らませない）。 */
    private static String truncated(String username) {
        return username == null || username.length() <= EmailAddress.MAX_LENGTH
                ? username
                : username.substring(0, EmailAddress.MAX_LENGTH);
    }
}
