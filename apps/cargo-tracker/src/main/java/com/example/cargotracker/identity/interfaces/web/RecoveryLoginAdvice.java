package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 回復コードでログインした印の知らせ（Bolt 33b、UI 設計「共通部品」の「回復コードでログインした印の知らせ」）。印がある間、ログインした
 * 画面のレイアウトが上部に「認証アプリを登録し直してください」を出す。A-08 は本文が同じことを言うので、画面が {@code false} で上書きする。
 */
@ControllerAdvice
public class RecoveryLoginAdvice {

    /** レイアウトが読むモデルの属性の名前。 */
    static final String NOTICE = "recoveryLoginNotice";

    @ModelAttribute(NOTICE)
    public boolean recoveryLoginNotice(HttpServletRequest request, Optional<AuthenticatedActor> actor) {
        return actor.isPresent() && RecoveryLogin.isMarked(request.getSession(false), actor.get());
    }
}
